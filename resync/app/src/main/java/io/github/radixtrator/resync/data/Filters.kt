package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What Sync's "Filter…" hides from feeds: communities, users and link domains, all lowercase. */
@Serializable
data class Filters(
    val subreddits: Set<String> = emptySet(),
    val users: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = subreddits.isEmpty() && users.isEmpty() && domains.isEmpty()

    /** A filtered community still shows when you open it ([feedCommunity]); users and domains are hidden everywhere. */
    fun hides(post: Post, feedCommunity: String?): Boolean =
        post.author.lowercase() in users ||
            post.domain.lowercase() in domains ||
            (post.subreddit.lowercase() in subreddits && !post.subreddit.equals(feedCommunity, ignoreCase = true))
}

class FilterStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _filters = MutableStateFlow(load())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    fun filterSubreddit(name: String, on: Boolean = true) = change { it.copy(subreddits = it.subreddits.toggle(name, on)) }

    fun filterUser(name: String, on: Boolean = true) = change { it.copy(users = it.users.toggle(name, on)) }

    fun filterDomain(domain: String, on: Boolean = true) = change { it.copy(domains = it.domains.toggle(domain, on)) }

    private fun Set<String>.toggle(value: String, on: Boolean) = if (on) this + value.lowercase() else this - value.lowercase()

    private fun change(transform: (Filters) -> Filters) {
        _filters.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(Filters.serializer(), _filters.value)) }
    }

    private fun load(): Filters =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(Filters.serializer(), it) }.getOrNull() } ?: Filters()

    private companion object {
        const val KEY = "filters.v1"
    }
}
