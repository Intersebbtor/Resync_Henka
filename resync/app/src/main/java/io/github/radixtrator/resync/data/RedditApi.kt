package io.github.radixtrator.resync.data

import kotlinx.serialization.json.jsonObject

/** The endpoints the app uses, on top of [RedditClient]. */
class RedditApi(private val client: RedditClient) {

    // --- Reading ---

    /** [subreddit] null = front page. Also accepts "popular", "all" and "a+b" multis. */
    suspend fun posts(subreddit: String?, sort: Sort, after: String?): Listing<Post> {
        val base = if (subreddit == null) "" else "/r/$subreddit"
        val json = client.get(
            "$base/${sort.path}",
            mapOf("after" to after, "limit" to "25", "t" to sort.time?.param),
        )
        return Parser.postListing(json)
    }

    /** The newest comments in [subreddit] (null = the front page's communities), for Sync's "Comments" sort. */
    suspend fun feedComments(subreddit: String?, after: String?): Listing<ProfileItem> {
        val base = if (subreddit == null) "" else "/r/$subreddit"
        return Parser.profileListing(client.get("$base/comments", mapOf("after" to after, "limit" to "25")))
    }

    /** [focusCommentId] narrows the page to that comment's subtree, with [context] parents above it. */
    suspend fun comments(
        postId: String,
        sort: CommentSort,
        focusCommentId: String? = null,
        context: Int = 0,
    ): PostWithComments = Parser.comments(
        client.get(
            "/comments/$postId",
            mapOf(
                "sort" to sort.param,
                "limit" to "200",
                "comment" to focusCommentId,
                "context" to context.takeIf { focusCommentId != null && it > 0 }?.toString(),
            ),
        ),
    )

    suspend fun moreChildren(postFullname: String, children: List<String>, sort: CommentSort): List<CommentNode> =
        Parser.moreChildren(
            client.get(
                "/api/morechildren",
                mapOf(
                    "api_type" to "json",
                    "link_id" to postFullname,
                    // The endpoint caps a single request at 100 IDs.
                    "children" to children.take(100).joinToString(","),
                    "sort" to sort.param,
                ),
            ),
        )

    suspend fun me(): Me = Parser.me(client.get("/api/v1/me"))

    /** The account's subscribed communities, A–Z, with icons for Explore. */
    suspend fun subscriptions(): List<Subreddit> = mine("subscriber")

    /** Communities the account moderates (Explore's Moderated section). */
    suspend fun moderated(): List<Subreddit> = mine("moderator")

    private suspend fun mine(where: String): List<Subreddit> {
        val subs = mutableListOf<Subreddit>()
        var after: String? = null
        do {
            val page = Parser.subredditListing(client.get("/subreddits/mine/$where", mapOf("limit" to "100", "after" to after)))
            subs += page.items
            after = page.after
        } while (after != null && subs.size < 1000)
        return subs.sortedBy { it.name.lowercase() }
    }

    suspend fun subredditAbout(name: String): Subreddit {
        val data = client.get("/r/$name/about").jsonObject.obj("data") ?: throw RedditException("No info for r/$name")
        return Parser.subreddit(data)
    }

    suspend fun user(name: String): UserProfile = Parser.user(client.get("/user/$name/about"))

    suspend fun userContent(name: String, where: UserWhere, sort: UserSort, after: String?): Listing<ProfileItem> =
        Parser.profileListing(
            client.get(
                "/user/$name/${where.path}",
                mapOf("sort" to sort.param, "t" to sort.time, "after" to after, "limit" to "25"),
            ),
        )

    suspend fun inbox(box: InboxBox, after: String?): Listing<InboxItem> =
        // mark=false: opening a box shouldn't silently mark everything read.
        Parser.inbox(client.get("/message/${box.path}", mapOf("after" to after, "limit" to "50", "mark" to "false")))

    suspend fun searchPosts(query: String, subreddit: String?, sort: SearchSort, after: String?): Listing<Post> {
        val path = if (subreddit == null) "/search" else "/r/$subreddit/search"
        return Parser.postListing(
            client.get(
                path,
                mapOf(
                    "q" to query,
                    "sort" to sort.param,
                    "t" to sort.time,
                    "type" to "link",
                    "restrict_sr" to subreddit?.let { "on" },
                    "after" to after,
                    "limit" to "25",
                ),
            ),
        )
    }

    suspend fun searchSubreddits(query: String, after: String?): Listing<Subreddit> =
        Parser.subredditListing(client.get("/subreddits/search", mapOf("q" to query, "after" to after, "limit" to "25")))

    // --- Writing ---

    /** [direction]: 1 up, 0 clear, -1 down. */
    suspend fun vote(fullname: String, direction: Int) {
        client.post("/api/vote", mapOf("id" to fullname, "dir" to direction.toString()))
    }

    suspend fun setSaved(fullname: String, saved: Boolean) {
        client.post(if (saved) "/api/save" else "/api/unsave", mapOf("id" to fullname))
    }

    suspend fun setHidden(fullname: String, hidden: Boolean) {
        client.post(if (hidden) "/api/hide" else "/api/unhide", mapOf("id" to fullname))
    }

    suspend fun setSubscribed(subreddit: String, subscribed: Boolean) {
        client.post("/api/subscribe", mapOf("action" to if (subscribed) "sub" else "unsub", "sr_name" to subreddit))
    }

    /** Replies to a post (t3_) or comment (t1_), or answers a message (t4_). */
    suspend fun reply(parentFullname: String, markdown: String): Comment? =
        Parser.postedComment(
            client.post("/api/comment", mapOf("api_type" to "json", "thing_id" to parentFullname, "text" to markdown)),
        )

    /** Returns the updated comment when editing a comment; null for self posts. */
    suspend fun edit(fullname: String, markdown: String): Comment? =
        Parser.postedComment(
            client.post("/api/editusertext", mapOf("api_type" to "json", "thing_id" to fullname, "text" to markdown)),
        )

    suspend fun delete(fullname: String) {
        client.post("/api/del", mapOf("id" to fullname))
    }

    /** Returns the new post's ID. [body] is markdown for self posts, the URL for links. */
    suspend fun submit(
        subreddit: String,
        title: String,
        isLink: Boolean,
        body: String,
        nsfw: Boolean,
        spoiler: Boolean,
    ): String = Parser.submittedId(
        client.post(
            "/api/submit",
            mapOf(
                "api_type" to "json",
                "sr" to subreddit,
                "kind" to if (isLink) "link" else "self",
                "title" to title,
                (if (isLink) "url" else "text") to body,
                "nsfw" to nsfw.toString(),
                "spoiler" to spoiler.toString(),
                "sendreplies" to "true",
                "resubmit" to "true",
            ),
        ),
    )

    suspend fun sendMessage(to: String, subject: String, markdown: String) {
        Parser.checkErrors(
            client.post("/api/compose", mapOf("api_type" to "json", "to" to to, "subject" to subject, "text" to markdown)),
        )
    }

    suspend fun markRead(fullnames: List<String>) {
        if (fullnames.isNotEmpty()) client.post("/api/read_message", mapOf("id" to fullnames.joinToString(",")))
    }

    suspend fun markAllRead() {
        client.post("/api/read_all_messages", emptyMap())
    }
}
