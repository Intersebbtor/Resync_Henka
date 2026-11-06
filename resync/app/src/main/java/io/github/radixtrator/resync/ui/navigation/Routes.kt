package io.github.radixtrator.resync.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.data.UserWhere
import kotlinx.serialization.Serializable

@Serializable
data object SetupRoute : NavKey

@Serializable
data object LoginRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/**
 * [home] screens are the bottom-navigation tabs (Sync's Posts and Inbox): they show the account
 * avatar instead of a back arrow, plus the navigation bar and the More actions FAB. Feeds picked in
 * Explore stay [home] too; feeds opened from a link are pushed as ordinary pages.
 */
@Serializable
data class InboxRoute(val home: Boolean = false) : NavKey

/** [subreddit] null = front page, unless [domain] asks for a site's posts (Sync's "Explore domain"). See [InboxRoute] for [home]. */
@Serializable
data class FeedRoute(val subreddit: String? = null, val home: Boolean = false, val domain: String? = null) : NavKey

/** [commentId] opens just that comment's thread, with [context] parent comments above it. */
@Serializable
data class PostRoute(val postId: String, val commentId: String? = null, val context: Int = 0) : NavKey

/** [postId]: the post the media came from, for the info sheet's Comments button. */
@Serializable
data class ImageRoute(val urls: List<String>, val start: Int = 0, val postId: String? = null) : NavKey

/** See [ImageRoute] for [postId]. */
@Serializable
data class VideoRoute(val url: String, val loop: Boolean = false, val postId: String? = null) : NavKey

@Serializable
data class UserRoute(val name: String, val tab: UserWhere = UserWhere.OVERVIEW) : NavKey

/** [communities] opens on the Communities tab (Explore's "Search all subs"). */
@Serializable
data class SearchRoute(val query: String = "", val subreddit: String? = null, val communities: Boolean = false) : NavKey

/** Sync's Swipe mode over [subreddit]'s feed (null = front page). */
@Serializable
data class SwipeModeRoute(val subreddit: String? = null) : NavKey

@Serializable
data class SubmitRoute(val subreddit: String? = null) : NavKey

enum class ComposeMode { REPLY, EDIT, MESSAGE }

/**
 * The markdown editor. REPLY/EDIT act on [target] (a fullname); [quote] is shown above the editor for
 * context. MESSAGE uses [to] and [subject].
 */
@Serializable
data class ComposeRoute(
    val mode: ComposeMode,
    val target: String? = null,
    val quote: String? = null,
    val initial: String = "",
    val to: String = "",
    val subject: String = "",
) : NavKey

/** Maps URLs onto in-app screens, like Sync did for links in posts and comments. */
object RedditLinks {
    private val POST = Regex(
        """^https?://(?:[\w-]+\.)?reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)(?:/[^/?#]*/([a-z0-9]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val SHORT = Regex("""^https?://redd\.it/([a-z0-9]+)/?$""", RegexOption.IGNORE_CASE)
    private val SUBREDDIT = Regex("""^https?://(?:[\w-]+\.)?reddit\.com/r/([\w+]+)/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val USER = Regex("""^https?://(?:[\w-]+\.)?reddit\.com/(?:u|user)/([\w-]+)/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val REDDIT_VIDEO = Regex("""^https?://v\.redd\.it/([a-z0-9]+)/?$""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""^https?://[^?#]+\.(?:jpe?g|png|gif|webp)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val GIFV = Regex("""^(https?://[^?#]+)\.gifv(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val MP4 = Regex("""^https?://[^?#]+\.mp4(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

    fun route(url: String): NavKey? {
        POST.find(url)?.let { m -> return PostRoute(m.groupValues[1], m.groupValues[2].ifEmpty { null }, context = if (m.groupValues[2].isEmpty()) 0 else 3) }
        SHORT.find(url)?.let { return PostRoute(it.groupValues[1]) }
        SUBREDDIT.find(url)?.let { return FeedRoute(it.groupValues[1]) }
        USER.find(url)?.let { return UserRoute(it.groupValues[1]) }
        REDDIT_VIDEO.find(url)?.let { return VideoRoute("https://v.redd.it/${it.groupValues[1]}/HLSPlaylist.m3u8") }
        GIFV.find(url)?.let { return VideoRoute(it.groupValues[1] + ".mp4", loop = true) }
        if (MP4.matches(url)) return VideoRoute(url)
        if (IMAGE.matches(url)) return ImageRoute(listOf(url))
        return null
    }
}
