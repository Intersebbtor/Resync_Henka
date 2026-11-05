package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Explore's sections, in the order the sheet shows them. */
enum class ExploreSection(val label: String) {
    FAVORITES("Favorites"),
    FEEDS("Feeds"),
    SUBSCRIPTIONS("Subscriptions"),
    MODERATED("Moderated"),
    MOST_VISITED("Most visited"),
}

/** A feed opened (`query` null) or a search run (`subreddit` null = all of Reddit), for "Recently searched & visited". */
@Serializable
data class Recent(val subreddit: String? = null, val query: String? = null)

@Serializable
data class Visits(val name: String, val count: Int)

/**
 * Sync's Explore memory. Feed names use "" for the front page; matching ignores case but the first
 * spelling seen is what's shown.
 */
@Serializable
data class ExploreState(
    val favorites: List<String> = emptyList(),
    /** Keyed by lowercase name. */
    val visits: Map<String, Visits> = emptyMap(),
    /** Newest first. */
    val recents: List<Recent> = emptyList(),
    /** Opened at launch instead of the front page; null = front page. */
    val defaultFeed: String? = null,
    val hidden: Set<ExploreSection> = emptySet(),
    /** Also list favorites in Subscriptions (Sync's "Duplicate favs"). */
    val duplicateFavorites: Boolean = false,
) {
    fun isFavorite(name: String) = favorites.any { it.equals(name, ignoreCase = true) }

    fun isDefault(name: String) = (defaultFeed ?: "").equals(name, ignoreCase = true)

    /** Most visited first, as Sync's "Top" section. */
    fun mostVisited(limit: Int = 20): List<Visits> = visits.values.sortedByDescending { it.count }.take(limit)
}

class ExploreStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    fun toggleFavorite(name: String) = update { s ->
        s.copy(favorites = if (s.isFavorite(name)) s.favorites.filterNot { it.equals(name, ignoreCase = true) } else s.favorites + name)
    }

    /** [name] null = front page. */
    fun recordVisit(name: String?) = update { s ->
        val feed = name.orEmpty()
        val key = feed.lowercase()
        val visits = s.visits[key]?.let { it.copy(count = it.count + 1) } ?: Visits(feed, 1)
        s.copy(visits = s.visits + (key to visits), recents = remember(s.recents, Recent(subreddit = feed)))
    }

    fun recordSearch(query: String, subreddit: String?) = update { s ->
        s.copy(recents = remember(s.recents, Recent(subreddit = subreddit, query = query.trim())))
    }

    fun resetVisits(name: String) = update { s -> s.copy(visits = s.visits - name.lowercase()) }

    fun clearVisits() = update { it.copy(visits = emptyMap()) }

    fun clearRecents() = update { it.copy(recents = emptyList()) }

    /** [name] "" or null = front page. */
    fun setDefault(name: String?) = update { it.copy(defaultFeed = name?.takeIf { n -> n.isNotEmpty() }) }

    fun setVisible(section: ExploreSection, visible: Boolean) = update { s ->
        s.copy(hidden = if (visible) s.hidden - section else s.hidden + section)
    }

    fun setDuplicateFavorites(on: Boolean) = update { it.copy(duplicateFavorites = on) }

    private fun remember(recents: List<Recent>, entry: Recent): List<Recent> {
        val same = { r: Recent -> r.query == entry.query && r.subreddit.equals(entry.subreddit, ignoreCase = true) }
        return (listOf(entry) + recents.filterNot(same)).take(MAX_RECENTS)
    }

    private fun update(change: (ExploreState) -> ExploreState) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit { putString(KEY, json.encodeToString(ExploreState.serializer(), next)) }
    }

    private fun load(): ExploreState = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(ExploreState.serializer(), it) }.getOrNull() }
        ?: ExploreState()

    private companion object {
        const val KEY = "explore.v1"
        const val MAX_RECENTS = 25
    }
}
