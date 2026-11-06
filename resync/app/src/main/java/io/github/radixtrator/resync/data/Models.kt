package io.github.radixtrator.resync.data

data class Listing<T>(val items: List<T>, val after: String?)

data class ImageSource(val url: String, val width: Int, val height: Int) {
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}

sealed interface PostMedia {
    data object None : PostMedia
    data class Image(val preview: ImageSource?, val url: String, val isGif: Boolean) : PostMedia
    data class Gallery(val preview: ImageSource?, val images: List<ImageSource>) : PostMedia
    /** [loop]: GIF-like clip (no audio, repeats) rather than a real video. */
    data class Video(val preview: ImageSource?, val url: String, val loop: Boolean = false) : PostMedia
    data class Link(val thumbnail: ImageSource?, val url: String) : PostMedia
}

data class Post(
    val id: String,
    val fullname: String,
    val subreddit: String,
    val author: String,
    val title: String,
    val selfText: String,
    val selfTextHtml: String?,
    val url: String,
    val domain: String,
    val permalink: String,
    val score: Int,
    val numComments: Int,
    val createdUtc: Long,
    val over18: Boolean,
    val spoiler: Boolean,
    val stickied: Boolean,
    val isSelf: Boolean,
    /** true = upvoted, false = downvoted, null = no vote. */
    val likes: Boolean?,
    val saved: Boolean,
    val flair: String?,
    val media: PostMedia,
    /** The flair's own background (ARGB), or null for a neutral chip. */
    val flairColor: Int? = null,
    /** Reddit's flair text color: dark text on a light chip, or light text on a dark one. */
    val flairDarkText: Boolean = true,
    val locked: Boolean = false,
    val authorFlair: String? = null,
    /** Share of votes that are upvotes, 0..1; null when Reddit didn't say. */
    val upvoteRatio: Float? = null,
    /** "moderator" or "admin" when the author posted as one. */
    val distinguished: String? = null,
    /** When the post was last edited (epoch seconds); null if never. */
    val editedUtc: Long? = null,
)

sealed interface CommentNode {
    val id: String
    val depth: Int
    val parentId: String
}

data class Comment(
    override val id: String,
    override val depth: Int,
    override val parentId: String,
    val fullname: String,
    val author: String,
    val body: String,
    val bodyHtml: String,
    val score: Int,
    val scoreHidden: Boolean,
    val createdUtc: Long,
    val edited: Boolean,
    val isSubmitter: Boolean,
    val distinguished: String?,
    val stickied: Boolean,
    val likes: Boolean?,
    val flair: String?,
    val saved: Boolean = false,
    /** When the comment was last edited (epoch seconds), if Reddit says. */
    val editedUtc: Long? = null,
    /** The author's account id (t2_…), for fetching their profile picture. */
    val authorFullname: String? = null,
) : CommentNode

/** A "load more comments" stub. Empty [children] means "continue this thread" (needs a new page). */
data class MoreComments(
    override val id: String,
    override val depth: Int,
    override val parentId: String,
    val count: Int,
    val children: List<String>,
) : CommentNode

data class PostWithComments(val post: Post, val comments: List<CommentNode>)

/** Listing sorts, as Sync's sort sheet lists them. [timed] sorts take a [TimeRange]. */
enum class SortKind(val path: String, val label: String, val timed: Boolean = false) {
    BEST("best", "Best"),
    HOT("hot", "Hot"),
    NEW("new", "New"),
    RISING("rising", "Rising"),
    TOP("top", "Top", timed = true),
    CONTROVERSIAL("controversial", "Controversial", timed = true),
    /** Sync's "Comments" sort: the feed's newest comments instead of its posts. */
    COMMENTS("comments", "Comments"),
}

enum class TimeRange(val param: String, val label: String) {
    HOUR("hour", "Hour"),
    DAY("day", "Day"),
    WEEK("week", "Week"),
    MONTH("month", "Month"),
    YEAR("year", "Year"),
    ALL("all", "All"),
}

data class Sort(val kind: SortKind, val time: TimeRange? = null) {
    val path: String get() = kind.path
    val label: String get() = time?.let { "${kind.label} · ${it.label}" } ?: kind.label

    companion object {
        val BEST = Sort(SortKind.BEST)
        val HOT = Sort(SortKind.HOT)
    }
}

/** Something to report a post or comment for: one of the community's rules, or one of Reddit's own. */
data class ReportReason(val text: String, val siteRule: Boolean)

/** One of a community's rules: its short name and, when it has one, the longer explanation. */
data class SubredditRule(val name: String, val descriptionHtml: String?)

/** One of a community's post flairs, for the Flairs chip. [color] is its background (ARGB), if it has one. */
data class LinkFlair(val text: String, val color: Int?, val id: String? = null)

/** The post flairs used in [posts], most used first. */
fun flairsOf(posts: List<Post>): List<LinkFlair> = posts
    .mapNotNull { post -> post.flair?.let { LinkFlair(it, post.flairColor) } }
    .groupBy { it.text }
    .entries
    .sortedByDescending { it.value.size }
    .map { it.value.first() }

data class Subreddit(
    val name: String,
    val fullname: String,
    val title: String,
    val publicDescription: String,
    val descriptionHtml: String?,
    val subscribers: Int,
    val iconUrl: String?,
    val bannerUrl: String?,
    val isSubscribed: Boolean,
    val over18: Boolean,
    val createdUtc: Long,
)

data class UserProfile(
    val name: String,
    val linkKarma: Int,
    val commentKarma: Int,
    val createdUtc: Long,
    val iconUrl: String?,
    val description: String?,
    val bannerUrl: String? = null,
)

/** A comment seen outside its thread (profiles, saved), so it carries its post's info. */
data class UserComment(
    val id: String,
    val fullname: String,
    val author: String,
    val body: String,
    val bodyHtml: String,
    val score: Int,
    val createdUtc: Long,
    val subreddit: String,
    val postId: String,
    val postTitle: String,
    val likes: Boolean?,
    val saved: Boolean,
    val scoreHidden: Boolean = false,
    val authorFlair: String? = null,
    val distinguished: String? = null,
)

/** A post or a comment: profiles mix them, and so do feeds on Sync's "Comments" sort. */
sealed interface ProfileItem {
    val fullname: String

    data class PostItem(val post: Post) : ProfileItem {
        override val fullname get() = post.fullname
    }

    data class CommentItem(val comment: UserComment) : ProfileItem {
        override val fullname get() = comment.fullname
    }
}

data class InboxItem(
    val id: String,
    val fullname: String,
    /** Comment/post replies and mentions are t1; private messages are t4. */
    val isComment: Boolean,
    val author: String,
    val dest: String,
    val subject: String,
    val postTitle: String?,
    val subreddit: String?,
    val body: String,
    val bodyHtml: String,
    val createdUtc: Long,
    val isNew: Boolean,
    val postId: String?,
    val commentId: String?,
)

data class Me(val name: String, val inboxCount: Int, val iconUrl: String? = null)

/** Sync's profile sections, in its order. The last four are only yours to see. */
enum class UserWhere(val path: String, val label: String, val ownOnly: Boolean = false) {
    OVERVIEW("overview", "Overview"),
    COMMENTS("comments", "Comments"),
    SUBMITTED("submitted", "Submitted"),
    UPVOTED("upvoted", "Upvoted", ownOnly = true),
    DOWNVOTED("downvoted", "Downvoted", ownOnly = true),
    SAVED("saved", "Saved", ownOnly = true),
    HIDDEN("hidden", "Hidden", ownOnly = true),
}

/** A Reddit trophy (14-Year Club, Verified Email…), for the profile's Trophies sheet. */
data class Trophy(val name: String, val iconUrl: String?, val description: String?)

enum class UserSort(val param: String, val label: String, val time: String? = null) {
    NEW("new", "New"),
    HOT("hot", "Hot"),
    TOP_WEEK("top", "Top: this week", "week"),
    TOP_ALL("top", "Top: all time", "all"),
}

enum class InboxBox(val path: String, val label: String) {
    INBOX("inbox", "All"),
    UNREAD("unread", "Unread"),
    MESSAGES("messages", "DMs"),
    SENT("sent", "Sent"),
    COMMENT_REPLIES("comments", "Comment replies"),
    POST_REPLIES("selfreply", "Post replies"),
    MENTIONS("mentions", "Mentions"),
    MOD("moderator", "Mod"),
    MOD_UNREAD("moderator/unread", "Mod unread"),
}

enum class SearchSort(val param: String, val label: String, val time: String? = null) {
    RELEVANCE("relevance", "Relevance"),
    NEW("new", "New"),
    HOT("hot", "Hot"),
    TOP_ALL("top", "Top", "all"),
    TOP_WEEK("top", "Top: this week", "week"),
    COMMENTS("comments", "Comments"),
}

/** Sync's search "Options" modes for post search; "Search subs" is the Communities tab instead. */
enum class SearchMode(val label: String, val hint: String) {
    SEARCH("Search", ""),
    SUBREDDIT("Subreddit", "Community, e.g. pics"),
    CUSTOM_FEED("Custom feed", "Multireddit, e.g. username/feedname"),
    PROFILE("Profile", "Username"),
    DOMAIN("Domain", "Domain, e.g. imgur.com"),
}

enum class CommentSort(val param: String, val label: String) {
    BEST("confidence", "Best"),
    TOP("top", "Top"),
    NEW("new", "New"),
    CONTROVERSIAL("controversial", "Controversial"),
    OLD("old", "Old"),
    QA("qa", "Q&A"),
}

/** One of your custom feeds (multireddits). [path] is like "user/bob/m/news"; [subreddits] are lowercase-agnostic names. */
data class Multi(val name: String, val path: String, val subreddits: List<String>) {
    /** The feed route's community string for it: "a+b+c", which the listing endpoints accept. */
    val feedName: String get() = subreddits.joinToString("+")
}

/** Sync's moderator sections for one community; [path] is under /r/<sub>/about/. */
enum class ModSection(val path: String, val label: String) {
    MODQUEUE("modqueue", "Modqueue"),
    REPORTS("reports", "Reports"),
    SPAM("spam", "Spam"),
    EDITED("edited", "Edited"),
    UNMODERATED("unmoderated", "Unmoderated"),
}
