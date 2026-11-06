package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What Sync's "Filter…" hides from feeds: communities, users and link domains, all lowercase, plus
 * [keywords] (matched anywhere in a title) and [flairs] (a post's flair text, exact match).
 */
@Serializable
data class Filters(
    val subreddits: Set<String> = emptySet(),
    val users: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val flairs: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = subreddits.isEmpty() && users.isEmpty() && domains.isEmpty() && keywords.isEmpty() && flairs.isEmpty()

    /** A filtered community still shows when you open it ([feedCommunity]); users and domains are hidden everywhere. */
    fun hides(post: Post, feedCommunity: String?): Boolean =
        post.author.lowercase() in users ||
            post.domain.lowercase() in domains ||
            (keywords.isNotEmpty() && post.title.lowercase().let { title -> keywords.any { it in title } }) ||
            (flairs.isNotEmpty() && post.flair?.trim()?.lowercase() in flairs) ||
            (post.subreddit.lowercase() in subreddits && !post.subreddit.equals(feedCommunity, ignoreCase = true))
}

/**
 * Sync's five Post filters lists (Settings › Filters), each edited on its own screen. [hint] is the add
 * field's label; [describe] turns a stored entry back into what the list shows.
 */
enum class FilterType(val title: String, val summary: String, val hint: String, val empty: String) {
    KEYWORDS("Keyword filters", "Hide posts by title keywords", "Word or phrase in the title", "No keyword filters"),
    DOMAINS("Domain filters", "Hide posts by domain", "Domain, like example.com", "No domain filters"),
    SUBREDDITS("Subreddit filters", "Hide posts by subreddit", "Community name", "No subreddit filters"),
    USERS("User filters", "Hide posts by users", "Username", "No user filters"),
    FLAIRS("Flair filters", "Hide posts by flair", "Flair text, exactly", "No flair filters"),
    ;

    fun of(filters: Filters): Set<String> = when (this) {
        KEYWORDS -> filters.keywords
        DOMAINS -> filters.domains
        SUBREDDITS -> filters.subreddits
        USERS -> filters.users
        FLAIRS -> filters.flairs
    }

    fun describe(entry: String): String = when (this) {
        KEYWORDS -> "\"$entry\""
        SUBREDDITS -> "r/$entry"
        USERS -> "u/$entry"
        DOMAINS, FLAIRS -> entry
    }

    /**
     * What gets stored for what was typed: lowercase and trimmed, without an "r/" or "u/" prefix for
     * communities and users, and without the scheme, "www." and path for a pasted link's domain (Reddit's
     * own `domain` field drops "www." too). Blank in, blank out.
     */
    fun normalize(raw: String): String {
        val v = raw.trim().lowercase()
        return when (this) {
            KEYWORDS, FLAIRS -> v
            SUBREDDITS -> v.removePrefix("/").removePrefix("r/").trim('/')
            USERS -> v.removePrefix("/").removePrefix("u/").removePrefix("user/").trim('/')
            DOMAINS -> v.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")
        }
    }
}

class FilterStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _filters = MutableStateFlow(load())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    fun filterSubreddit(name: String, on: Boolean = true) = set(FilterType.SUBREDDITS, name, on)

    fun filterUser(name: String, on: Boolean = true) = set(FilterType.USERS, name, on)

    fun filterDomain(domain: String, on: Boolean = true) = set(FilterType.DOMAINS, domain, on)

    fun filterKeyword(word: String, on: Boolean = true) = set(FilterType.KEYWORDS, word, on)

    fun filterFlair(flair: String, on: Boolean = true) = set(FilterType.FLAIRS, flair, on)

    /** Adds ([on]) or removes [value] from [type]'s list, after [FilterType.normalize]; blank values are ignored. */
    fun set(type: FilterType, value: String, on: Boolean = true) {
        val v = type.normalize(value)
        if (v.isEmpty()) return
        change { f -> f.with(type) { if (on) it + v else it - v } }
    }

    /** Removes a stored [entry] exactly as listed (older entries may predate [FilterType.normalize]). */
    fun remove(type: FilterType, entry: String) = change { f -> f.with(type) { it - entry - type.normalize(entry) } }

    /** Sync's "Remove all" on a filter list. */
    fun clear(type: FilterType) = change { f -> f.with(type) { emptySet() } }

    private fun Filters.with(type: FilterType, transform: (Set<String>) -> Set<String>): Filters = when (type) {
        FilterType.KEYWORDS -> copy(keywords = transform(keywords))
        FilterType.DOMAINS -> copy(domains = transform(domains))
        FilterType.SUBREDDITS -> copy(subreddits = transform(subreddits))
        FilterType.USERS -> copy(users = transform(users))
        FilterType.FLAIRS -> copy(flairs = transform(flairs))
    }

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
