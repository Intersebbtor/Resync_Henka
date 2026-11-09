package io.github.radixtrator.resync.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Maps Reddit's "thing" JSON (kinds t1 = comment, t3 = post, more) onto [Models]. */
object Parser {

    private val IMAGE_URL = Regex(""".*\.(jpe?g|png|gif|webp)(\?.*)?$""", RegexOption.IGNORE_CASE)
    private const val PREVIEW_WIDTH = 1080

    fun postListing(root: JsonElement): Listing<Post> {
        val data = root.jsonObject.obj("data") ?: return Listing(emptyList(), null)
        val posts = data.arr("children").orEmpty().mapNotNull { child ->
            val thing = child as? JsonObject ?: return@mapNotNull null
            if (thing.string("kind") != "t3") return@mapNotNull null
            thing.obj("data")?.let(::post)
        }
        return Listing(posts, data.string("after"))
    }

    fun subredditListing(root: JsonElement): Listing<Subreddit> {
        val data = root.jsonObject.obj("data") ?: return Listing(emptyList(), null)
        val subs = data.arr("children").orEmpty().mapNotNull { child ->
            val thing = child as? JsonObject ?: return@mapNotNull null
            if (thing.string("kind") != "t5") return@mapNotNull null
            thing.obj("data")?.let(::subreddit)
        }
        return Listing(subs, data.string("after"))
    }

    fun subreddit(d: JsonObject): Subreddit {
        val name = d.string("display_name").orEmpty()
        return Subreddit(
            name = name,
            fullname = d.string("name") ?: "t5_${d.string("id").orEmpty()}",
            title = d.string("title").orEmpty(),
            publicDescription = d.string("public_description").orEmpty(),
            descriptionHtml = d.string("description_html")?.takeIf { it.isNotBlank() },
            subscribers = d.int("subscribers") ?: 0,
            // community_icon is the current one; icon_img is the legacy fallback. Unset ones are "".
            iconUrl = d.firstUrl("community_icon", "icon_img"),
            bannerUrl = d.firstUrl("banner_background_image", "banner_img"),
            isSubscribed = d.bool("user_is_subscriber"),
            over18 = d.bool("over18"),
            createdUtc = d.long("created_utc") ?: 0,
        )
    }

    private fun JsonObject.firstUrl(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> string(key)?.takeIf { it.startsWith("http") } }

    /**
     * Flair text. Richtext flairs carry their emoji as separate parts, which `*_flair_text` spells
     * out as `:name:` placeholders, so only their text parts are kept.
     */
    private fun flair(d: JsonObject, prefix: String): String? {
        val parts = d.arr("${prefix}_flair_richtext")?.mapNotNull { it as? JsonObject }.orEmpty()
        val text = if (parts.isEmpty()) {
            d.string("${prefix}_flair_text")
        } else {
            parts.filter { it.string("e") == "text" }.joinToString("") { it.string("t").orEmpty() }
        }
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun user(root: JsonElement): UserProfile {
        val d = root.jsonObject.obj("data") ?: error("No user data")
        return UserProfile(
            name = d.string("name").orEmpty(),
            linkKarma = d.int("link_karma") ?: 0,
            commentKarma = d.int("comment_karma") ?: 0,
            createdUtc = d.long("created_utc") ?: 0,
            iconUrl = userIcon(d),
            description = d.obj("subreddit")?.string("public_description")?.takeIf { it.isNotBlank() },
            bannerUrl = d.obj("subreddit")?.string("banner_img")?.takeIf { it.startsWith("http") },
        )
    }

    /** /api/v1/user/name/trophies. */
    fun trophies(root: JsonElement): List<Trophy> =
        root.jsonObject.obj("data")?.arr("trophies").orEmpty().mapNotNull { element ->
            val d = (element as? JsonObject)?.obj("data") ?: return@mapNotNull null
            val name = d.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Trophy(name, (d.string("icon_70") ?: d.string("icon_40"))?.takeIf { it.startsWith("http") }, d.string("description")?.takeIf { it.isNotBlank() })
        }

    /** The avatar on /about and /api/v1/me: the snoovatar if set, else the profile icon. */
    private fun userIcon(d: JsonObject): String? =
        (d.string("snoovatar_img")?.takeIf { it.isNotBlank() } ?: d.string("icon_img"))?.takeIf { it.startsWith("http") }

    /** User overview/saved listings mix posts (t3) and comments (t1). */
    fun profileListing(root: JsonElement): Listing<ProfileItem> {
        val data = root.jsonObject.obj("data") ?: return Listing(emptyList(), null)
        val items = data.arr("children").orEmpty().mapNotNull { child ->
            val thing = child as? JsonObject ?: return@mapNotNull null
            val d = thing.obj("data") ?: return@mapNotNull null
            when (thing.string("kind")) {
                "t3" -> ProfileItem.PostItem(post(d))
                "t1" -> ProfileItem.CommentItem(userComment(d))
                else -> null
            }
        }
        return Listing(items, data.string("after"))
    }

    private fun userComment(d: JsonObject): UserComment {
        val id = d.string("id").orEmpty()
        return UserComment(
            id = id,
            fullname = d.string("name") ?: "t1_$id",
            author = d.string("author") ?: "[deleted]",
            body = d.string("body").orEmpty(),
            bodyHtml = d.string("body_html").orEmpty(),
            score = d.int("score") ?: 0,
            createdUtc = d.long("created_utc") ?: 0,
            subreddit = d.string("subreddit").orEmpty(),
            postId = d.string("link_id").orEmpty().removePrefix("t3_"),
            postTitle = d.string("link_title").orEmpty(),
            likes = d.boolOrNull("likes"),
            saved = d.bool("saved"),
            scoreHidden = d.bool("score_hidden"),
            authorFlair = flair(d, "author"),
            distinguished = d.string("distinguished"),
        )
    }

    // "/r/sub/comments/<post>/<slug>/<comment>/?context=3"
    private val CONTEXT = Regex("""/comments/([a-z0-9]+)/[^/]*/([a-z0-9]+)""", RegexOption.IGNORE_CASE)

    fun inbox(root: JsonElement): Listing<InboxItem> {
        val data = root.jsonObject.obj("data") ?: return Listing(emptyList(), null)
        val items = data.arr("children").orEmpty().mapNotNull { child ->
            val thing = child as? JsonObject ?: return@mapNotNull null
            val d = thing.obj("data") ?: return@mapNotNull null
            val kind = thing.string("kind")
            if (kind != "t1" && kind != "t4") return@mapNotNull null
            val id = d.string("id").orEmpty()
            val context = CONTEXT.find(d.string("context").orEmpty())
            InboxItem(
                id = id,
                fullname = d.string("name") ?: "${kind}_$id",
                isComment = kind == "t1",
                author = d.string("author") ?: "[deleted]",
                dest = d.string("dest").orEmpty(),
                subject = d.string("subject").orEmpty(),
                postTitle = d.string("link_title"),
                subreddit = d.string("subreddit"),
                body = d.string("body").orEmpty(),
                bodyHtml = d.string("body_html").orEmpty(),
                createdUtc = d.long("created_utc") ?: 0,
                isNew = d.bool("new"),
                postId = context?.groupValues?.get(1),
                commentId = context?.groupValues?.get(2),
            )
        }
        return Listing(items, data.string("after"))
    }

    fun me(root: JsonElement): Me {
        val d = root.jsonObject
        return Me(name = d.string("name").orEmpty(), inboxCount = d.int("inbox_count") ?: 0, iconUrl = userIcon(d))
    }

    /** api_type=json responses report failures as `json.errors: [[code, message, field], ...]`. */
    fun checkErrors(root: JsonElement): JsonElement {
        val errors = (root as? JsonObject)?.obj("json")?.arr("errors").orEmpty()
        if (errors.isNotEmpty()) {
            val text = errors.joinToString("; ") { e ->
                val parts = (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                parts.getOrNull(1) ?: parts.firstOrNull() ?: "error"
            }
            throw RedditException(text.replaceFirstChar { it.uppercase() })
        }
        return root
    }

    /** The comment returned by /api/comment or /api/editusertext. */
    fun postedComment(root: JsonElement): Comment? {
        val thing = checkErrors(root).jsonObject.obj("json")?.obj("data")?.arr("things")?.firstOrNull() as? JsonObject
        return thing?.let { node(it, depth = 0) } as? Comment
    }

    /** Post ID from /api/submit. */
    fun submittedId(root: JsonElement): String =
        checkErrors(root).jsonObject.obj("json")?.obj("data")?.string("id")
            ?: throw RedditException("Reddit didn't return the new post")

    fun post(d: JsonObject): Post {
        val id = d.string("id").orEmpty()
        return Post(
            id = id,
            fullname = d.string("name") ?: "t3_$id",
            subreddit = d.string("subreddit").orEmpty(),
            author = d.string("author") ?: "[deleted]",
            title = d.string("title").orEmpty(),
            selfText = d.string("selftext").orEmpty(),
            selfTextHtml = d.string("selftext_html"),
            url = d.string("url_overridden_by_dest") ?: d.string("url").orEmpty(),
            domain = d.string("domain").orEmpty(),
            permalink = d.string("permalink").orEmpty(),
            score = d.int("score") ?: 0,
            numComments = d.int("num_comments") ?: 0,
            createdUtc = d.long("created_utc") ?: 0,
            over18 = d.bool("over_18"),
            spoiler = d.bool("spoiler"),
            stickied = d.bool("stickied"),
            isSelf = d.bool("is_self"),
            likes = d.boolOrNull("likes"),
            saved = d.bool("saved"),
            flair = flair(d, "link"),
            media = media(d),
            flairColor = flairColor(d.string("link_flair_background_color")),
            flairDarkText = d.string("link_flair_text_color") != "light",
            locked = d.bool("locked"),
            authorFlair = flair(d, "author"),
            upvoteRatio = (d["upvote_ratio"] as? JsonPrimitive)?.content?.toFloatOrNull(),
            distinguished = d.string("distinguished"),
            // "edited" is false, or the edit timestamp.
            editedUtc = d.long("edited"),
        )
    }

    /**
     * /api/user_data_by_account_ids: each account's profile picture by fullname. NSFW profile pictures are
     * left out, so those authors keep their letter avatar.
     */
    fun avatars(root: JsonElement): Map<String, String> = (root as? JsonObject).orEmpty().mapNotNull { (id, value) ->
        val d = value as? JsonObject ?: return@mapNotNull null
        if (d.bool("profile_over_18")) return@mapNotNull null
        d.string("profile_img")?.takeIf { it.startsWith("http") }?.let { id to it }
    }.toMap()

    /** /api/multi/mine: an array of LabeledMulti things. */
    fun multis(root: JsonElement): List<Multi> = (root as? JsonArray).orEmpty().mapNotNull { element ->
        val d = (element as? JsonObject)?.obj("data") ?: return@mapNotNull null
        val name = d.string("display_name") ?: d.string("name") ?: return@mapNotNull null
        val path = d.string("path")?.trim('/') ?: return@mapNotNull null
        Multi(name, path, d.arr("subreddits").orEmpty().mapNotNull { (it as? JsonObject)?.string("name") })
    }

    /** /r/sub/about/rules, in the community's order. */
    fun rules(root: JsonElement): List<SubredditRule> = root.jsonObject.arr("rules").orEmpty().mapNotNull { element ->
        val d = element as? JsonObject ?: return@mapNotNull null
        val name = d.string("short_name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        SubredditRule(name, d.string("description_html")?.takeIf { it.isNotBlank() })
    }

    /**
     * /r/sub/about/rules as report reasons: the community's rules that apply to [comment]s or posts, then
     * Reddit's site-wide ones.
     */
    fun reportReasons(root: JsonElement, comment: Boolean): List<ReportReason> {
        val data = root.jsonObject
        val kind = if (comment) "comment" else "link"
        val rules = data.arr("rules").orEmpty().mapNotNull { element ->
            val d = element as? JsonObject ?: return@mapNotNull null
            if (d.string("kind").let { it != null && it != "all" && it != kind }) return@mapNotNull null
            (d.string("violation_reason") ?: d.string("short_name"))?.trim()?.takeIf { it.isNotEmpty() }?.let { ReportReason(it, siteRule = false) }
        }
        val site = data.arr("site_rules").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)?.let { r -> ReportReason(r, siteRule = true) } }
        return (rules + site).distinctBy { it.text }
    }

    /** /r/sub/about/moderators: the moderators' names, in Reddit's order (longest serving first). */
    fun moderators(root: JsonElement): List<String> =
        root.jsonObject.obj("data")?.arr("children").orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }

    /** /r/sub/api/link_flair_v2: an array of flair templates. Blank ones (emoji-only) are dropped. */
    fun linkFlairs(root: JsonElement): List<LinkFlair> = (root as? JsonArray).orEmpty().mapNotNull { element ->
        val d = element as? JsonObject ?: return@mapNotNull null
        val text = d.string("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        LinkFlair(text, flairColor(d.string("background_color")), d.string("id"))
    }.distinctBy { it.text }

    /**
     * GET /api/mod/conversations: `conversations` is a map by id, `conversationIds` their order (newest first).
     * Anything else (demo data, an empty answer) reads as no conversations.
     */
    fun modMailConversations(root: JsonElement): List<ModMailConversation> {
        val o = root as? JsonObject ?: return emptyList()
        val byId = o.obj("conversations") ?: return emptyList()
        val order = o.arr("conversationIds")?.mapNotNull { (it as? JsonPrimitive)?.content } ?: byId.keys.toList()
        return order.mapNotNull { id -> byId.obj(id)?.let { modMailConversation(it, id) } }
    }

    /**
     * GET or POST /api/mod/conversations/{id}: the conversation plus a `messages` map, put in the order of the
     * conversation's `objIds` (oldest first), else by date.
     */
    fun modMailThread(root: JsonElement): ModMailThread {
        val o = root as? JsonObject ?: throw RedditException("Reddit didn't return the conversation")
        val c = o.obj("conversation") ?: throw RedditException("Reddit didn't return the conversation")
        val conversation = modMailConversation(c, c.string("id").orEmpty())
        val byId = o.obj("messages")
        val messages = byId.orEmpty().mapNotNull { (id, value) -> (value as? JsonObject)?.let { modMailMessage(it, id) } }
        val order = c.arr("objIds").orEmpty().mapNotNull { e ->
            (e as? JsonObject)?.takeIf { it.string("key") == "messages" }?.string("id")
        }
        val sorted = if (order.isNotEmpty() && messages.all { it.id in order }) {
            messages.sortedBy { order.indexOf(it.id) }
        } else {
            messages.sortedBy { it.dateUtc }
        }
        return ModMailThread(conversation, sorted)
    }

    private fun modMailConversation(c: JsonObject, fallbackId: String): ModMailConversation = ModMailConversation(
        id = c.string("id") ?: fallbackId,
        subject = c.string("subject").orEmpty(),
        community = c.obj("owner")?.string("displayName").orEmpty(),
        participants = c.arr("authors").orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }.distinct(),
        lastUpdatedUtc = isoSeconds(c.string("lastUpdated")),
        numMessages = c.int("numMessages") ?: 0,
        highlighted = c.bool("isHighlighted"),
        unread = c.string("lastUnread") != null,
        archived = c.int("state") == 2,
    )

    private fun modMailMessage(m: JsonObject, fallbackId: String): ModMailMessage {
        val author = m.obj("author")
        return ModMailMessage(
            id = m.string("id") ?: fallbackId,
            author = author?.string("name") ?: "[deleted]",
            bodyHtml = m.string("body").orEmpty(),
            bodyMarkdown = m.string("bodyMarkdown").orEmpty(),
            dateUtc = isoSeconds(m.string("date")),
            authorIsMod = author?.bool("isMod") ?: false,
            authorHidden = author?.bool("isHidden") ?: false,
            internal = m.bool("isInternal"),
        )
    }

    /** Mod mail's timestamps, "2018-06-04T19:49:33.104036+00:00", to epoch seconds; 0 if missing or unreadable. */
    internal fun isoSeconds(text: String?): Long =
        text?.let { runCatching { java.time.OffsetDateTime.parse(it).toEpochSecond() }.getOrNull() } ?: 0L

    /** "#46d160" → opaque ARGB; "" or "transparent" (no color set) → null. */
    private fun flairColor(hex: String?): Int? {
        val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
        return digits.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    }

    fun comments(root: JsonElement): PostWithComments {
        val parts = root.jsonArray
        val post = postListing(parts[0]).items.firstOrNull() ?: error("Comment page has no post")
        val out = mutableListOf<CommentNode>()
        flatten(parts.getOrNull(1)?.jsonObject?.obj("data")?.arr("children"), 0, out)
        return PostWithComments(post, out)
    }

    /** /api/morechildren returns a flat list; reorder it so every parent precedes its replies. */
    fun moreChildren(root: JsonElement): List<CommentNode> {
        val things = root.jsonObject.obj("json")?.obj("data")?.arr("things").orEmpty()
        val nodes = things.mapNotNull { thing -> (thing as? JsonObject)?.let { node(it, depth = 0) } }
        return treeOrder(nodes)
    }

    internal fun treeOrder(nodes: List<CommentNode>): List<CommentNode> {
        val ids = nodes.mapTo(HashSet()) { "t1_${it.id}" }
        val byParent = nodes.groupBy { it.parentId }
        val out = ArrayList<CommentNode>(nodes.size)
        fun visit(node: CommentNode) {
            out += node
            byParent["t1_${node.id}"]?.forEach(::visit)
        }
        nodes.filter { it.parentId !in ids }.forEach(::visit)
        return out
    }

    private fun flatten(children: JsonArray?, depth: Int, out: MutableList<CommentNode>) {
        children?.forEach { child ->
            val thing = child as? JsonObject ?: return@forEach
            val node = node(thing, depth) ?: return@forEach
            out += node
            if (node is Comment) {
                val replies = thing.obj("data")?.obj("replies")
                flatten(replies?.obj("data")?.arr("children"), node.depth + 1, out)
            }
        }
    }

    private fun node(thing: JsonObject, depth: Int): CommentNode? {
        val d = thing.obj("data") ?: return null
        val id = d.string("id") ?: return null
        val actualDepth = d.int("depth") ?: depth
        val parent = d.string("parent_id").orEmpty()
        return when (thing.string("kind")) {
            "t1" -> Comment(
                id = id,
                depth = actualDepth,
                parentId = parent,
                fullname = d.string("name") ?: "t1_$id",
                author = d.string("author") ?: "[deleted]",
                body = d.string("body").orEmpty(),
                bodyHtml = d.string("body_html").orEmpty(),
                score = d.int("score") ?: 0,
                scoreHidden = d.bool("score_hidden"),
                createdUtc = d.long("created_utc") ?: 0,
                // "edited" is false, or the edit timestamp.
                edited = d.long("edited") != null || d.bool("edited"),
                isSubmitter = d.bool("is_submitter"),
                distinguished = d.string("distinguished"),
                stickied = d.bool("stickied"),
                likes = d.boolOrNull("likes"),
                flair = flair(d, "author"),
                saved = d.bool("saved"),
                editedUtc = d.long("edited"),
                authorFullname = d.string("author_fullname"),
            )
            "more" -> MoreComments(
                id = id,
                depth = actualDepth,
                parentId = parent,
                count = d.int("count") ?: 0,
                children = d.arr("children").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
            )
            else -> null
        }
    }

    private fun media(d: JsonObject): PostMedia {
        val url = d.string("url_overridden_by_dest") ?: d.string("url").orEmpty()
        val previewImage = d.obj("preview")?.arr("images")?.firstOrNull() as? JsonObject
        val preview = previewImage?.let(::pickResolution)

        if (d.bool("is_gallery")) {
            val meta = d.obj("media_metadata")
            val items = d.obj("gallery_data")?.arr("items").orEmpty()
            val images = items.mapNotNull { item ->
                val mediaId = (item as? JsonObject)?.string("media_id") ?: return@mapNotNull null
                val source = meta?.obj(mediaId)?.obj("s") ?: return@mapNotNull null
                val src = source.string("u") ?: source.string("gif") ?: return@mapNotNull null
                ImageSource(src, source.int("x") ?: 0, source.int("y") ?: 0)
            }
            if (images.isNotEmpty()) {
                val firstPreviews = meta?.obj((items.first() as JsonObject).string("media_id").orEmpty())?.arr("p")
                val cardPreview = firstPreviews?.let { pickFromList(it) } ?: images.first()
                return PostMedia.Gallery(cardPreview, images)
            }
        }

        val redditVideo = d.obj("secure_media")?.obj("reddit_video") ?: d.obj("media")?.obj("reddit_video")
        if (d.bool("is_video") && redditVideo != null) {
            val stream = redditVideo.string("hls_url") ?: redditVideo.string("fallback_url") ?: url
            return PostMedia.Video(preview, stream, loop = redditVideo.bool("is_gif"))
        }
        // Externally hosted GIF/video that Reddit transcoded itself (gfycat-era links, imgur, etc.).
        val transcoded = d.obj("preview")?.obj("reddit_video_preview")
        if (!d.bool("is_self") && transcoded != null) {
            (transcoded.string("hls_url") ?: transcoded.string("fallback_url"))
                ?.let { return PostMedia.Video(preview, it, loop = transcoded.bool("is_gif")) }
        }
        // Reddit transcodes GIFs to mp4 in preview.images[0].variants.mp4.
        val gifMp4 = previewImage?.obj("variants")?.obj("mp4")?.obj("source")?.string("url")

        return when {
            d.bool("is_self") -> PostMedia.None
            url.endsWith(".gifv", ignoreCase = true) -> PostMedia.Video(preview, url.dropLast(5) + ".mp4", loop = true)
            d.string("post_hint") == "image" || IMAGE_URL.matches(url) ->
                PostMedia.Image(preview, url, isGif = url.contains(".gif", ignoreCase = true) && gifMp4 == null)
            gifMp4 != null -> PostMedia.Video(preview, gifMp4, loop = true)
            else -> {
                val thumb = preview ?: d.string("thumbnail")?.takeIf { it.startsWith("http") }
                    ?.let { ImageSource(it, d.int("thumbnail_width") ?: 0, d.int("thumbnail_height") ?: 0) }
                PostMedia.Link(thumb, url)
            }
        }
    }

    /** Smallest preview at least [PREVIEW_WIDTH] wide, else the source; narrower ones ride along in [ImageSource.smaller]. */
    private fun pickResolution(image: JsonObject): ImageSource? {
        val source = image.obj("source")?.let(::imageSource)
        val all = image.arr("resolutions").orEmpty().mapNotNull { (it as? JsonObject)?.let(::imageSource) }
        return withSmaller(all.firstOrNull { it.width >= PREVIEW_WIDTH } ?: source, all)
    }

    private fun pickFromList(list: JsonArray): ImageSource? {
        val all = list.mapNotNull { (it as? JsonObject)?.let { o -> o.string("u")?.let { u -> ImageSource(u, o.int("x") ?: 0, o.int("y") ?: 0) } } }
        return withSmaller(all.firstOrNull { it.width >= PREVIEW_WIDTH } ?: all.lastOrNull(), all)
    }

    private fun withSmaller(chosen: ImageSource?, all: List<ImageSource>): ImageSource? =
        chosen?.copy(smaller = all.filter { it.width in 1 until chosen.width }.sortedBy { it.width })

    private fun imageSource(o: JsonObject): ImageSource? =
        o.string("url")?.let { ImageSource(it, o.int("width") ?: 0, o.int("height") ?: 0) }
}
