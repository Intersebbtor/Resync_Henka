package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A post the user opened, as the comment page's "Recents" sheet lists it. */
@Serializable
data class RecentPost(val id: String, val title: String, val subreddit: String)

/**
 * Sync's "Recents" on the comment page: the posts opened most recently, newest first, keeping [MAX].
 * Reopening a post moves it back to the top. Local only.
 */
class RecentPostsStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _posts = MutableStateFlow(load())
    val posts: StateFlow<List<RecentPost>> = _posts.asStateFlow()

    fun record(post: Post) = record(RecentPost(post.id, post.title, post.subreddit))

    fun record(post: RecentPost) {
        val next = (listOf(post) + _posts.value.filter { it.id != post.id }).take(MAX)
        if (next == _posts.value) return
        _posts.value = next
        prefs.edit { putString(KEY, json.encodeToString(ListSerializer(RecentPost.serializer()), next)) }
    }

    fun clear() {
        _posts.value = emptyList()
        prefs.edit { remove(KEY) }
    }

    private fun load(): List<RecentPost> =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(RecentPost.serializer()), it) }.getOrNull() }
            .orEmpty()

    companion object {
        const val MAX = 50
        private const val KEY = "recent_posts.v1"
    }
}
