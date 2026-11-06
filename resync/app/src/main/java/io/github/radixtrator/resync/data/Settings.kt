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

/** Sync's "Base font size": scales every sp in the app. SMALLER is Sync's default on phones. */
enum class BaseFontSize(val label: String, val scale: Float) {
    SMALLEST("Smallest", 0.8f),
    SMALLER("Smaller", 0.9f),
    NORMAL("Normal", 1.0f),
    LARGER("Larger", 1.1f),
    LARGEST("Largest", 1.25f),
}

/** Typeface choices. Roboto is bundled (Sync's default); Product Sans can't be, so the rest are the system's families. */
enum class Typeface(val label: String) {
    ROBOTO("Roboto"),
    SANS("Sans serif (system)"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
}

enum class NsfwMode(val label: String) { HIDE("Hide NSFW posts"), BLUR("Blur previews"), SHOW("Show") }

/**
 * Sync's "Periodically check for new messages". [minutes] null means no repeating check. WorkManager can't
 * repeat faster than every 15 minutes, so Sync's "Every 10 minutes" is 15 here.
 */
enum class MessageCheck(val label: String, val minutes: Long?) {
    OFF("Off", null),
    MINUTES_15("Every 15 minutes", 15),
    MINUTES_30("Every 30 minutes", 30),
    HOUR("Every 60 minutes", 60),
    HOURS_3("Every 3 hours", 180),
    HOURS_6("Every 6 hours", 360),
    HOURS_12("Every 12 hours", 720),
    HOURS_24("Every 24 hours", 1440),
    ON_LAUNCH("On launch", null),
}

/** Sync's "HD Reddit video quality": when v.redd.it videos open in HD rather than SD (the player's HD button switches). */
enum class VideoQuality(val label: String) { ALWAYS("Always"), WIFI("WiFi only"), NEVER("Never") }

/** Sync's "Post media preview size" for the image or video above a post's comments. */
enum class MediaPreviewSize(val label: String) { CROPPED("Cropped height"), FULL("Full height") }

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
    /** Sync's "Hide read": posts you had already opened when a feed loaded are left out of it. */
    val hideRead: Boolean = false,
    val commentSort: CommentSort = CommentSort.BEST,
    val postMediaPreview: MediaPreviewSize = MediaPreviewSize.CROPPED,
    val swipeBack: Boolean = true,
    val baseFontSize: BaseFontSize = BaseFontSize.SMALLER,
    val typeface: Typeface = Typeface.ROBOTO,
    /** Sync's "Action visibility": labels of More actions buttons switched off. */
    val hiddenActions: Set<String> = emptySet(),
    val inAppBrowser: Boolean = true,
    val muteVideos: Boolean = false,
    val hdVideo: VideoQuality = VideoQuality.NEVER,
    /** Sync's comment navigation bar: previous/next top-level comment buttons. Off by default, as in Sync. */
    val commentNavigation: Boolean = false,
    /** Reopening a post returns to where you were, with the same threads folded. */
    val rememberCommentPosition: Boolean = true,
    /** Footnote numbers on off-Reddit links, with a card per link under the text. */
    val linkCards: Boolean = true,
    /** Sync's "Show profile pictures": authors' avatars in comment headers instead of their initial. */
    val profilePictures: Boolean = true,
    val messageCheck: MessageCheck = MessageCheck.HOUR,
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
/** The saved base font scale, read before the container exists (MainActivity applies it in attachBaseContext). */
fun savedBaseFontScale(prefs: SharedPreferences): Float =
    prefs.getString("settings.v1", null)
        ?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(Settings.serializer(), it).baseFontSize.scale }.getOrNull() }
        ?: BaseFontSize.SMALLER.scale

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
