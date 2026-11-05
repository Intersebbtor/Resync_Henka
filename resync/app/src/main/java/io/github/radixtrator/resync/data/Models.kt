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

enum class Sort(val path: String, val label: String, val time: String? = null) {
    BEST("best", "Best"),
    HOT("hot", "Hot"),
    NEW("new", "New"),
    RISING("rising", "Rising"),
    TOP_DAY("top", "Top: today", "day"),
    TOP_WEEK("top", "Top: this week", "week"),
    TOP_MONTH("top", "Top: this month", "month"),
    TOP_YEAR("top", "Top: this year", "year"),
    TOP_ALL("top", "Top: all time", "all"),
}

data class Subreddit(
    val name: String,
    val fullname: String,
    val title: String,
    val publicDescription: String,
    val descriptionHtml: String?,
    val subscribers: Int,
    val activeUsers: Int,
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
)

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

data class Me(val name: String, val inboxCount: Int)

enum class UserWhere(val path: String, val label: String, val ownOnly: Boolean = false) {
    OVERVIEW("overview", "Overview"),
    SUBMITTED("submitted", "Posts"),
    COMMENTS("comments", "Comments"),
    SAVED("saved", "Saved", ownOnly = true),
    UPVOTED("upvoted", "Upvoted", ownOnly = true),
}

enum class UserSort(val param: String, val label: String, val time: String? = null) {
    NEW("new", "New"),
    HOT("hot", "Hot"),
    TOP_WEEK("top", "Top: this week", "week"),
    TOP_ALL("top", "Top: all time", "all"),
}

enum class InboxBox(val path: String, val label: String) {
    INBOX("inbox", "All"),
    UNREAD("unread", "Unread"),
    MESSAGES("messages", "Messages"),
    SENT("sent", "Sent"),
}

enum class SearchSort(val param: String, val label: String, val time: String? = null) {
    RELEVANCE("relevance", "Relevance"),
    HOT("hot", "Hot"),
    NEW("new", "New"),
    TOP_WEEK("top", "Top: this week", "week"),
    TOP_ALL("top", "Top: all time", "all"),
    COMMENTS("comments", "Most comments"),
}

enum class CommentSort(val param: String, val label: String) {
    BEST("confidence", "Best"),
    TOP("top", "Top"),
    NEW("new", "New"),
    CONTROVERSIAL("controversial", "Controversial"),
    OLD("old", "Old"),
    QA("qa", "Q&A"),
}
