package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A watched community and the creation time (epoch seconds) of the newest post already notified about or skipped. */
@Serializable
data class WatchedCommunity(val name: String, val newestSeen: Long)

/** A watched post (Sync's post Watch) and the comment count already notified about. */
@Serializable
data class WatchedPost(val id: String, val title: String, val subreddit: String, val comments: Int)

/**
 * Communities and posts with Sync's Watch on. The background check notifies about posts newer than
 * [WatchedCommunity.newestSeen] and comments past [WatchedPost.comments], so watching starts from the moment
 * it's turned on. Communities are keyed by lowercase name, posts by id.
 */
class WatchStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _watched = MutableStateFlow(load())
    val watched: StateFlow<Map<String, WatchedCommunity>> = _watched.asStateFlow()
    private val _posts = MutableStateFlow(loadPosts())
    val posts: StateFlow<Map<String, WatchedPost>> = _posts.asStateFlow()

    val isEmpty: Boolean get() = _watched.value.isEmpty() && _posts.value.isEmpty()

    fun isWatchingPost(id: String) = id in _posts.value

    fun watchPost(post: Post) = changePosts { it + (post.id to WatchedPost(post.id, post.title, post.subreddit, post.numComments)) }

    fun unwatchPost(id: String) = changePosts { it - id }

    fun markComments(id: String, count: Int) = changePosts { all ->
        val current = all[id] ?: return@changePosts all
        all + (id to current.copy(comments = maxOf(current.comments, count)))
    }

    fun isWatching(name: String) = name.lowercase() in _watched.value

    fun watch(name: String, since: Long) = change { it + (name.lowercase() to WatchedCommunity(name, since)) }

    fun unwatch(name: String) = change { it - name.lowercase() }

    fun markSeen(name: String, newest: Long) = change { all ->
        val key = name.lowercase()
        val current = all[key] ?: return@change all
        all + (key to current.copy(newestSeen = maxOf(current.newestSeen, newest)))
    }

    private fun change(transform: (Map<String, WatchedCommunity>) -> Map<String, WatchedCommunity>) {
        _watched.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(ListSerializer(WatchedCommunity.serializer()), _watched.value.values.toList())) }
    }

    private fun changePosts(transform: (Map<String, WatchedPost>) -> Map<String, WatchedPost>) {
        _posts.update(transform)
        prefs.edit { putString(POSTS_KEY, json.encodeToString(ListSerializer(WatchedPost.serializer()), _posts.value.values.toList())) }
    }

    private fun loadPosts(): Map<String, WatchedPost> =
        prefs.getString(POSTS_KEY, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(WatchedPost.serializer()), it) }.getOrNull() }
            ?.associateBy { it.id }
            .orEmpty()

    private fun load(): Map<String, WatchedCommunity> =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(WatchedCommunity.serializer()), it) }.getOrNull() }
            ?.associateBy { it.name.lowercase() }
            .orEmpty()

    private companion object {
        const val KEY = "watched.v1"
        const val POSTS_KEY = "watchedposts.v1"
    }
}
