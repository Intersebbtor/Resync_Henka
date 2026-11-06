package io.github.radixtrator.resync.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.ContentEvent
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentNode
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.CommentTree
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMemory
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.ui.common.likesFor
import io.github.radixtrator.resync.ui.common.voteValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostViewModel(
    private val container: AppContainer,
    private val postId: String,
    /** Show only this comment's subtree (plus [context] parents), like Reddit's permalinks. */
    val focusCommentId: String?,
    private val context: Int,
) : ViewModel() {

    data class State(
        val post: Post? = null,
        val comments: List<CommentNode> = emptyList(),
        val collapsed: Set<String> = emptySet(),
        val sort: CommentSort = CommentSort.BEST,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val loadingMore: Set<String> = emptySet(),
        val error: String? = null,
        val message: String? = null,
        val postDeleted: Boolean = false,
        /** Sports mode: comments reload by themselves every [sportsRefreshSeconds]. */
        val sportsMode: Boolean = false,
    )

    companion object {
        private const val AUTOMOD = "AutoModerator"
    }

    private val _state = MutableStateFlow(State(sort = container.settings.settings.value.commentSort))
    val state = _state.asStateFlow()

    private val memoryKey = PostMemoryStore.key(postId, focusCommentId)
    private val settings get() = container.settings.settings.value
    private val remembering get() = settings.rememberCommentPosition

    /** Where the user left this page last time, to restore once comments load. */
    val savedPosition: PostMemory? = container.postMemory[memoryKey]?.takeIf { remembering }

    private val _subredditIcon = MutableStateFlow<String?>(null)
    /** The post's community icon for the toolbar, fetched once the post is known. */
    val subredditIcon = _subredditIcon.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(refresh = false)
        viewModelScope.launch { container.events.collect(::onEvent) }
    }

    /** [quiet]: a background refresh (sports mode) that shows no spinner and keeps quiet about failures. */
    fun load(refresh: Boolean, quiet: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = !refresh, refreshing = refresh && !quiet, error = if (quiet) it.error else null) }
        loadJob = viewModelScope.launch {
            try {
                val result = container.api.comments(postId, _state.value.sort, focusCommentId, context, settings.commentCount.limit)
                val comments = shown(result.comments)
                _state.update {
                    // Keep folds across refreshes, and restore last visit's on first load. With no last visit,
                    // "Collapse all threads by default" folds every top-level thread (not on single-thread pages).
                    val folded = when {
                        it.post != null -> it.collapsed
                        savedPosition != null -> savedPosition.collapsed
                        settings.collapseAllByDefault && focusCommentId == null -> topLevelIds(comments)
                        else -> emptySet()
                    }
                    val ids = comments.mapTo(HashSet()) { node -> node.id }
                    it.copy(
                        post = result.post,
                        comments = comments,
                        collapsed = folded.filterTo(HashSet()) { id -> id in ids },
                        loading = false,
                        refreshing = false,
                    )
                }
                if (_subredditIcon.value == null) loadSubredditIcon(result.post.subreddit)
                loadAvatars(comments)
                container.recentPosts.record(result.post)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) {
                    _state.update { it.copy(loading = false, refreshing = false) }
                } else {
                    _state.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Something went wrong") }
                }
            }
        }
    }

    private var sportsJob: Job? = null

    /** Sync's "Live comments refresh rate": sports mode's interval, read afresh for every wait. */
    val sportsRefreshSeconds: Int get() = settings.liveRefresh.seconds

    /**
     * Sync's "Sports mode" (live threads): sort by New and reload the comments every [sportsRefreshSeconds]
     * until it's turned off. Rows are keyed by comment id, so the list stays on what the user was reading.
     */
    fun setSportsMode(on: Boolean) {
        if (on == _state.value.sportsMode) return
        sportsJob?.cancel()
        sportsJob = null
        _state.update { it.copy(sportsMode = on) }
        if (!on) return
        if (_state.value.sort != CommentSort.NEW) setSort(CommentSort.NEW)
        sportsJob = viewModelScope.launch {
            while (true) {
                delay(sportsRefreshSeconds * 1000L)
                // Don't cut off a load the user started (pull to refresh, a sort change).
                if (loadJob?.isActive != true) load(refresh = true, quiet = true)
            }
        }
    }

    private fun onEvent(event: ContentEvent) {
        val post = _state.value.post ?: return
        when (event) {
            is ContentEvent.Replied -> _state.update { s ->
                if (event.parentFullname == post.fullname) {
                    s.copy(comments = listOf(event.comment.copy(depth = 0, parentId = post.fullname)) + s.comments)
                } else {
                    val index = s.comments.indexOfFirst { it is Comment && it.fullname == event.parentFullname }
                    if (index < 0) return@update s
                    val parent = s.comments[index]
                    val reply = event.comment.copy(depth = parent.depth + 1, parentId = event.parentFullname)
                    s.copy(
                        comments = s.comments.subList(0, index + 1) + reply + s.comments.subList(index + 1, s.comments.size),
                        collapsed = s.collapsed - parent.id,
                    )
                }
            }
            is ContentEvent.Edited -> when {
                event.fullname == post.fullname -> load(refresh = true)
                else -> updateComment(event.fullname) {
                    it.copy(body = event.body, bodyHtml = event.bodyHtml ?: it.bodyHtml, edited = true)
                }
            }
            is ContentEvent.Deleted -> when {
                event.fullname == post.fullname -> _state.update { it.copy(postDeleted = true) }
                else -> updateComment(event.fullname) {
                    it.copy(author = "[deleted]", body = "[deleted]", bodyHtml = "<div class=\"md\"><p>[deleted]</p></div>")
                }
            }
        }
    }

    fun setSort(sort: CommentSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort, comments = emptyList()) }
        load(refresh = false)
    }

    fun toggleCollapsed(comment: Comment) {
        _state.update { it.copy(collapsed = if (comment.id in it.collapsed) it.collapsed - comment.id else it.collapsed + comment.id) }
        rememberFolds()
    }

    /** Sync's "Collapse all": fold every top-level thread down to its first comment. */
    fun collapseAll() {
        _state.update { s -> s.copy(collapsed = topLevelIds(s.comments)) }
        rememberFolds()
    }

    private fun topLevelIds(nodes: List<CommentNode>): Set<String> =
        nodes.filter { it is Comment && it.depth == 0 }.mapTo(HashSet()) { it.id }

    /**
     * The comments as the Comments settings want them: "Hide AutoMod comments" drops AutoModerator's comments with
     * everything under them, and "Show user flair" off drops authors' flair.
     */
    private fun shown(nodes: List<CommentNode>): List<CommentNode> {
        val s = settings
        if (!s.hideAutoMod && s.commentFlair) return nodes
        val out = ArrayList<CommentNode>(nodes.size)
        var skipBelow = -1 // depth of a hidden bot comment whose replies are being skipped
        for (node in nodes) {
            if (skipBelow >= 0) {
                if (node.depth > skipBelow) continue
                skipBelow = -1
            }
            if (s.hideAutoMod && node is Comment && node.author.equals(AUTOMOD, ignoreCase = true)) {
                skipBelow = node.depth
                continue
            }
            out += if (!s.commentFlair && node is Comment && node.flair != null) node.copy(flair = null) else node
        }
        return out
    }

    private fun rememberFolds() {
        if (remembering) container.postMemory.saveCollapsed(memoryKey, _state.value.collapsed)
    }

    fun saveScroll(index: Int, offset: Int) {
        if (remembering && _state.value.post != null) container.postMemory.saveScroll(memoryKey, index, offset)
    }

    private fun loadAvatars(nodes: List<CommentNode>) {
        if (!container.settings.settings.value.profilePictures) return
        viewModelScope.launch { container.avatars.load(nodes.mapNotNull { (it as? Comment)?.authorFullname }) }
    }

    private fun loadSubredditIcon(subreddit: String) {
        viewModelScope.launch {
            runCatching { container.api.subredditAbout(subreddit) }.onSuccess { _subredditIcon.value = it.iconUrl }
        }
    }

    fun loadMore(more: MoreComments) {
        val post = _state.value.post ?: return
        if (more.children.isEmpty() || more.id in _state.value.loadingMore) return
        _state.update { it.copy(loadingMore = it.loadingMore + more.id) }
        viewModelScope.launch {
            try {
                val loaded = container.api.moreChildren(post.fullname, more.children, _state.value.sort)
                loadAvatars(loaded)
                _state.update {
                    it.copy(
                        comments = shown(CommentTree.splice(it.comments, more, loaded, remaining = more.children.drop(100))),
                        loadingMore = it.loadingMore - more.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = it.loadingMore - more.id, message = "Couldn't load replies: ${e.message}") }
            }
        }
    }

    fun votePost(direction: Int) {
        val post = _state.value.post ?: return
        if (!requireLogin()) return
        _state.update { it.copy(post = post.copy(likes = likesFor(direction), score = post.score - voteValue(post.likes) + direction)) }
        viewModelScope.launch {
            runCatching { container.api.vote(post.fullname, direction) }.onFailure { e ->
                _state.update { it.copy(post = post, message = "Vote failed: ${e.message}") }
            }
        }
    }

    fun toggleSavePost() {
        val post = _state.value.post ?: return
        if (!requireLogin()) return
        _state.update { it.copy(post = post.copy(saved = !post.saved)) }
        viewModelScope.launch {
            runCatching { container.api.setSaved(post.fullname, !post.saved) }.onFailure { e ->
                _state.update { it.copy(post = post, message = "Save failed: ${e.message}") }
            }
        }
    }

    fun voteComment(comment: Comment, direction: Int) {
        if (!requireLogin()) return
        updateComment(comment.fullname) { it.copy(likes = likesFor(direction), score = it.score - voteValue(it.likes) + direction) }
        viewModelScope.launch {
            runCatching { container.api.vote(comment.fullname, direction) }.onFailure { e ->
                updateComment(comment.fullname) { it.copy(likes = comment.likes, score = comment.score) }
                _state.update { it.copy(message = "Vote failed: ${e.message}") }
            }
        }
    }

    fun toggleSaveComment(comment: Comment) {
        if (!requireLogin()) return
        updateComment(comment.fullname) { it.copy(saved = !comment.saved) }
        viewModelScope.launch {
            runCatching { container.api.setSaved(comment.fullname, !comment.saved) }.onFailure { e ->
                updateComment(comment.fullname) { it.copy(saved = comment.saved) }
                _state.update { it.copy(message = "Save failed: ${e.message}") }
            }
        }
    }

    fun delete(fullname: String) {
        viewModelScope.launch {
            runCatching { container.api.delete(fullname) }
                .onSuccess { container.emit(ContentEvent.Deleted(fullname)) }
                .onFailure { e -> _state.update { it.copy(message = "Delete failed: ${e.message}") } }
        }
    }

    private fun updateComment(fullname: String, change: (Comment) -> Comment) = _state.update { s ->
        s.copy(comments = s.comments.map { if (it is Comment && it.fullname == fullname) change(it) else it })
    }

    fun requireLogin(): Boolean {
        if (container.username.value != null) return true
        _state.update { it.copy(message = "Log in first") }
        return false
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
