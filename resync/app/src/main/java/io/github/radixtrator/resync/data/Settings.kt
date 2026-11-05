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

/** What a swipe stage does. Sync's post and comment swipe lists, merged; [posts]/[comments] say where each applies. */
enum class SwipeAction(val label: String, val posts: Boolean, val comments: Boolean) {
    UPVOTE("Upvote", posts = true, comments = true),
    DOWNVOTE("Downvote", posts = true, comments = true),
    SAVE("Save", posts = true, comments = true),
    PROFILE("Profile", posts = true, comments = true),
    SHARE("Share", posts = true, comments = true),
    HIDE("Hide", posts = true, comments = false),
    MARK_READ("Mark as read", posts = true, comments = false),
    REPLY("Reply", posts = false, comments = true),
}

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
    /** Sync's comment navigation bar: previous/next top-level comment buttons. Off by default, as in Sync. */
    val commentNavigation: Boolean = false,
    /** Reopening a post returns to where you were, with the same threads folded. */
    val rememberCommentPosition: Boolean = true,
    /** Footnote numbers on off-Reddit links, with a card per link under the text. */
    val linkCards: Boolean = true,
    /** Sync's extended swipe: drag left, and each further step arms the next of these three. */
    val postSwipe: Boolean = true,
    val postSwipeActions: List<SwipeAction> = DEFAULT_POST_SWIPES,
    val commentSwipe: Boolean = true,
    val commentSwipeActions: List<SwipeAction> = DEFAULT_COMMENT_SWIPES,
    /** Per-feed overrides of [layout], keyed by [feedKey], like Sync's per-subreddit view. */
    val feedLayouts: Map<String, PostLayout> = emptyMap(),
) {
    fun layoutFor(subreddit: String?): PostLayout = feedLayouts[feedKey(subreddit)] ?: layout

    /** The three post swipe stages, repairing a stored list that isn't three valid entries. */
    val postSwipeStages: List<SwipeAction>
        get() = postSwipeActions.takeIf { l -> l.size == 3 && l.all { it.posts } } ?: DEFAULT_POST_SWIPES

    val commentSwipeStages: List<SwipeAction>
        get() = commentSwipeActions.takeIf { l -> l.size == 3 && l.all { it.comments } } ?: DEFAULT_COMMENT_SWIPES

    companion object {
        // Sync's defaults: posts upvote, downvote, hide; comments upvote, save, reply.
        val DEFAULT_POST_SWIPES = listOf(SwipeAction.UPVOTE, SwipeAction.DOWNVOTE, SwipeAction.HIDE)
        val DEFAULT_COMMENT_SWIPES = listOf(SwipeAction.UPVOTE, SwipeAction.SAVE, SwipeAction.REPLY)

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
