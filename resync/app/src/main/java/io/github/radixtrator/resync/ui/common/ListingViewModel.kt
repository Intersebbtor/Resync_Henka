package io.github.radixtrator.resync.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.ContentEvent
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingState<T>(
    val items: List<T> = emptyList(),
    val after: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Paging for any Reddit listing: first page, append on scroll, pull-to-refresh, retry.
 * Lists that contain posts also get optimistic vote/save/hide/delete by overriding [postOf]/[withPost].
 */
abstract class ListingViewModel<T>(protected val container: AppContainer) : ViewModel() {
    protected val _state = MutableStateFlow(ListingState<T>())
    val state: StateFlow<ListingState<T>> = _state.asStateFlow()
    private var loadJob: Job? = null

    protected abstract suspend fun fetch(after: String?): Listing<T>
    protected abstract fun keyOf(item: T): String
    protected open fun postOf(item: T): Post? = null
    protected open fun withPost(item: T, post: Post): T = item

    init {
        viewModelScope.launch {
            container.events.collect { event ->
                if (event is ContentEvent.Deleted) updateItems { list -> list.filterNot { keyOf(it) == event.fullname } }
            }
        }
    }

    fun refresh() = reload(showRefresh = true)

    fun reload(showRefresh: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = !showRefresh, refreshing = showRefresh, error = null, endReached = false) }
        loadJob = viewModelScope.launch { fetchInto(after = null) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.endReached || s.error != null || s.after == null) return
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch { fetchInto(after = s.after) }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) reload() else {
            _state.update { it.copy(error = null) }
            loadMore()
        }
    }

    /** [after] null = first page (replaces items); otherwise appends. */
    private suspend fun fetchInto(after: String?) {
        try {
            val page = fetch(after)
            _state.update {
                // Listings shift while paging, so a page can repeat items; list keys must stay unique.
                val items = if (after == null) page.items else it.items + page.items
                it.copy(
                    items = items.distinctBy(::keyOf),
                    after = page.after,
                    endReached = page.after == null,
                    loading = false,
                    refreshing = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Something went wrong") }
        }
    }

    protected fun updateItems(change: (List<T>) -> List<T>) = _state.update { it.copy(items = change(it.items)) }

    protected fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun messageShown() = _state.update { it.copy(message = null) }

    protected fun requireLogin(): Boolean {
        if (container.username.value != null) return true
        showMessage("Log in first")
        return false
    }

    // --- Post actions ---

    private fun updatePost(fullname: String, change: (Post) -> Post) = updateItems { list ->
        list.map { item -> postOf(item)?.takeIf { it.fullname == fullname }?.let { withPost(item, change(it)) } ?: item }
    }

    fun vote(post: Post, direction: Int) {
        if (!requireLogin()) return
        updatePost(post.fullname) { it.copy(likes = likesFor(direction), score = it.score - voteValue(it.likes) + direction) }
        viewModelScope.launch {
            runCatching { container.api.vote(post.fullname, direction) }.onFailure { e ->
                updatePost(post.fullname) { it.copy(likes = post.likes, score = post.score) }
                showMessage("Vote failed: ${e.message}")
            }
        }
    }

    fun toggleSave(post: Post) {
        if (!requireLogin()) return
        updatePost(post.fullname) { it.copy(saved = !post.saved) }
        viewModelScope.launch {
            runCatching { container.api.setSaved(post.fullname, !post.saved) }.onFailure { e ->
                updatePost(post.fullname) { it.copy(saved = post.saved) }
                showMessage("Save failed: ${e.message}")
            }
        }
    }

    fun hide(post: Post) {
        if (!requireLogin()) return
        val before = _state.value.items
        updateItems { list -> list.filterNot { postOf(it)?.fullname == post.fullname } }
        viewModelScope.launch {
            runCatching { container.api.setHidden(post.fullname, true) }
                .onSuccess { showMessage("Post hidden") }
                .onFailure { e ->
                    updateItems { before }
                    showMessage("Hide failed: ${e.message}")
                }
        }
    }

    fun delete(post: Post) {
        viewModelScope.launch {
            runCatching { container.api.delete(post.fullname) }
                .onSuccess { container.emit(ContentEvent.Deleted(post.fullname)) }
                .onFailure { e -> showMessage("Delete failed: ${e.message}") }
        }
    }
}
