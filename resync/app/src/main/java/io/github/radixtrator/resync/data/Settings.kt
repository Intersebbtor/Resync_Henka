package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark"), BLACK("Black (AMOLED)") }

/** Sync's six feed layouts, in its Change view order. Names are persisted, so CARD/COMPACT keep theirs. */
enum class PostLayout(val label: String) {
    LIST("List"),
    COMPACT("Compact"),
    SMALLER_CARDS("Smaller cards"),
    SMALL_CARDS("Small cards"),
    CARD("Cards"),
    SLIDES("Slides"),
}

enum class NsfwMode(val label: String) { HIDE("Hide NSFW posts"), BLUR("Blur previews"), SHOW("Show") }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val layout: PostLayout = PostLayout.CARD,
    val nsfw: NsfwMode = NsfwMode.BLUR,
    val blurSpoilers: Boolean = true,
    val commentSort: CommentSort = CommentSort.BEST,
    val swipeBack: Boolean = true,
    val inAppBrowser: Boolean = true,
    val muteVideos: Boolean = false,
    /** Per-feed overrides of [layout], keyed by [feedKey], like Sync's per-subreddit view. */
    val feedLayouts: Map<String, PostLayout> = emptyMap(),
) {
    fun layoutFor(subreddit: String?): PostLayout = feedLayouts[feedKey(subreddit)] ?: layout

    companion object {
        /** Front page is ""; subreddits and multis by lowercase name. */
        fun feedKey(subreddit: String?): String = subreddit?.lowercase().orEmpty()
    }
}

/** Persists [Settings] as one JSON blob; unknown/removed fields are ignored on load. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit { putString(KEY, json.encodeToString(Settings.serializer(), next)) }
    }

    private fun load(): Settings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
        ?: Settings()

    private companion object {
        const val KEY = "settings.v1"
    }
}
