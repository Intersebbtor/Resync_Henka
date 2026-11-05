package io.github.radixtrator.resync

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.radixtrator.resync.data.ApiConfig
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.CommentTree
import io.github.radixtrator.resync.data.DemoWrites
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.ui.common.armedStage
import io.github.radixtrator.resync.ui.common.firstParagraph
import io.github.radixtrator.resync.ui.common.footnoteLinks
import io.github.radixtrator.resync.ui.common.isImageLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.RedditException
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.hasSpoiler
import io.github.radixtrator.resync.ui.compose.insertLink
import io.github.radixtrator.resync.ui.compose.prefixLines
import io.github.radixtrator.resync.ui.compose.wrap
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.ImageRoute
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.RedditLinks
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.navigation.VideoRoute
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Fixtures are the debug demo assets (synthetic, generated), so tests and demo mode can't drift apart.
private fun fixture(name: String) =
    RedditClient.JSON.parseToJsonElement(File("src/debug/assets/demo/$name").readText())

class ParserTest {

    @Test
    fun `listing maps every media kind`() {
        val listing = Parser.postListing(fixture("listing.json"))
        assertEquals("t3_dm0007", listing.after)
        val byId = listing.items.associateBy { it.id }

        assertIs<PostMedia.None>(byId.getValue("dm0001").media)
        assertTrue(byId.getValue("dm0001").stickied)

        val image = assertIs<PostMedia.Image>(byId.getValue("dm0002").media)
        assertEquals(1080, image.preview?.width, "picks the first resolution at least 1080 wide")

        val gallery = assertIs<PostMedia.Gallery>(byId.getValue("dm0003").media)
        assertEquals(3, gallery.images.size)
        assertEquals(1080, gallery.preview?.width)

        assertIs<PostMedia.Link>(byId.getValue("dm0004").media)

        val video = assertIs<PostMedia.Video>(byId.getValue("dm0005").media)
        assertTrue(video.url.endsWith(".m3u8"))

        assertTrue(byId.getValue("dm0006").spoiler)
    }

    @Test
    fun `comment tree flattens depth first with more stubs`() {
        val page = Parser.comments(fixture("comments.json"))
        assertEquals("dm0007", page.post.id)
        val ids = page.comments.map { it.id to it.depth }
        assertEquals(
            listOf("ca1" to 0, "cb1" to 0, "cb2" to 1, "cb3" to 2, "cb4" to 3, "cb9" to 1, "cc1" to 0, "cd1" to 0, "ce1" to 0),
            ids,
        )
        val first = page.comments.first() as Comment
        assertEquals("moderator", first.distinguished)
        assertTrue((page.comments.first { it.id == "cc1" } as Comment).edited, "numeric edited timestamp counts as edited")
        assertTrue((page.comments.first { it.id == "cb4" } as Comment).isSubmitter)
        assertEquals(true, (page.comments.first { it.id == "cd1" } as Comment).likes)
        assertEquals(listOf("cb5", "cb6"), (page.comments.first { it.id == "cb9" } as MoreComments).children)
    }

    @Test
    fun `collapsing hides descendants and counts them`() {
        val all = Parser.comments(fixture("comments.json")).comments
        val visible = CommentTree.visible(all, collapsed = setOf("cb1"))
        assertEquals(listOf("ca1", "cb1", "cc1", "cd1", "ce1"), visible.map { it.node.id })
        assertEquals(3, visible.first { it.node.id == "cb1" }.hiddenCount, "cb2, cb3, cb4; the more stub isn't a comment")

        val nested = CommentTree.visible(all, collapsed = setOf("cb2"))
        assertEquals(2, nested.first { it.node.id == "cb2" }.hiddenCount)
        assertTrue(nested.any { it.node.id == "cb9" }, "sibling more stub at the collapsed comment's depth stays visible")
    }

    @Test
    fun `morechildren is reordered and spliced with fixed depths`() {
        val all = Parser.comments(fixture("comments.json")).comments
        val loaded = Parser.moreChildren(fixture("morechildren.json"))
        assertEquals(listOf("cb5", "cb6"), loaded.map { it.id }, "parent before reply")

        val more = all.first { it.id == "cb9" } as MoreComments
        val spliced = CommentTree.splice(all, more, loaded, remaining = emptyList())
        val after = spliced.map { it.id to it.depth }
        val at = after.indexOf("cb5" to 1)
        assertTrue(at > 0, "cb5 placed at depth 1: $after")
        assertEquals("cb6" to 2, after[at + 1])
        assertTrue(spliced.none { it is MoreComments && it.id == "cb9" })

        val partial = CommentTree.splice(all, more, loaded.take(1), remaining = listOf("x1"))
        val leftover = partial.filterIsInstance<MoreComments>().first { it.id == "cb9" }
        assertEquals(listOf("x1"), leftover.children)
    }

    @Test
    fun `client id accepts the email line`() {
        assertEquals("AbC_def-12345", ApiConfig.parseClientId("App ID: AbC_def-12345"))
        assertEquals("AbC_def-12345", ApiConfig.parseClientId("  AbC_def-12345 \n"))
        assertNull(ApiConfig.parseClientId("short"))
        assertNull(ApiConfig.parseClientId("has spaces? no!"))
    }

    @Test
    fun `reddit links route in app`() {
        assertEquals(PostRoute("abc123"), RedditLinks.route("https://www.reddit.com/r/Android/comments/abc123/some_title/"))
        assertEquals(PostRoute("abc123"), RedditLinks.route("https://old.reddit.com/comments/abc123"))
        assertEquals(PostRoute("abc123"), RedditLinks.route("https://redd.it/abc123"))
        assertEquals(FeedRoute("productivity"), RedditLinks.route("https://www.reddit.com/r/productivity"))
        assertEquals(FeedRoute("pics+aww"), RedditLinks.route("https://reddit.com/r/pics+aww/"))
        assertEquals(UserRoute("someone"), RedditLinks.route("https://www.reddit.com/user/someone"))
        assertEquals(UserRoute("some-one"), RedditLinks.route("https://reddit.com/u/some-one/"))
        assertEquals(
            PostRoute("abc123", "def456", context = 3),
            RedditLinks.route("https://www.reddit.com/r/Android/comments/abc123/title/def456/?context=3"),
        )
        assertEquals(ImageRoute(listOf("https://i.redd.it/x1.jpg")), RedditLinks.route("https://i.redd.it/x1.jpg"))
        assertEquals(VideoRoute("https://v.redd.it/abc/HLSPlaylist.m3u8"), RedditLinks.route("https://v.redd.it/abc"))
        assertEquals(VideoRoute("https://i.imgur.com/abc.mp4", loop = true), RedditLinks.route("https://i.imgur.com/abc.gifv"))
        assertNull(RedditLinks.route("https://example.com/r/foo"))
        assertNull(RedditLinks.route("https://example.com/article"))
    }

    @Test
    fun `gif-like media loops, real video doesn't`() {
        val byId = Parser.postListing(fixture("listing.json")).items.associateBy { it.id }
        assertEquals(false, assertIs<PostMedia.Video>(byId.getValue("dm0005").media).loop)
        assertTrue(assertIs<PostMedia.Image>(byId.getValue("dm0008").media).isGif)
    }

    @Test
    fun `inbox parses replies and messages with thread context`() {
        val inbox = Parser.inbox(fixture("inbox.json")).items
        assertEquals(3, inbox.size)
        val reply = inbox[0]
        assertTrue(reply.isComment && reply.isNew)
        assertEquals("dm0007", reply.postId)
        assertEquals("cb4", reply.commentId)
        val message = inbox[1]
        assertTrue(!message.isComment)
        assertEquals("t4_m1", message.fullname)
        assertNull(message.postId)
    }

    @Test
    fun `profile, about and community search parse`() {
        val user = Parser.user(fixture("user_about.json"))
        assertEquals("early_bird", user.name)
        assertEquals(48211, user.commentKarma)

        val items = Parser.profileListing(fixture("user_overview.json")).items
        assertEquals(listOf("t1_ub1", "t3_dm0002", "t1_ub2"), items.map { it.fullname })
        val comment = assertIs<ProfileItem.CommentItem>(items[0]).comment
        assertEquals("dm0007", comment.postId)
        assertIs<ProfileItem.PostItem>(items[1])

        val about = Parser.subreddit(fixture("subreddit_about.json").jsonObject.getValue("data").jsonObject)
        assertEquals("Android", about.name)
        assertTrue(about.isSubscribed)
        assertTrue(about.iconUrl!!.startsWith("https://"))

        assertEquals(3, Parser.subredditListing(fixture("subreddit_search.json")).items.size)
        assertEquals(2, Parser.me(fixture("me.json")).inboxCount)
    }

    @Test
    fun `blank icons fall back and richtext flair drops emoji`() {
        // Shapes seen on the live API: unset icons are "", richtext flair spells emoji as :name:.
        val sub = RedditClient.JSON.parseToJsonElement(
            """{"display_name":"Android","community_icon":"","icon_img":"https://b.thumbs.redditmedia.com/a.png"}""",
        ).jsonObject
        assertEquals("https://b.thumbs.redditmedia.com/a.png", Parser.subreddit(sub).iconUrl)

        val post = RedditClient.JSON.parseToJsonElement(
            """{"id":"p1","title":"t","link_flair_text":":edit: Impression/Review",
               "link_flair_richtext":[{"e":"emoji","a":":edit:","u":"https://emoji.redditmedia.com/e.png"},
                                      {"e":"text","t":" Impression/Review"}]}""",
        ).jsonObject
        assertEquals("Impression/Review", Parser.post(post).flair)
    }

    @Test
    fun `flair colors, locks and author flair parse`() {
        val colored = Parser.post(
            RedditClient.JSON.parseToJsonElement(
                """{"id":"p2","title":"t","link_flair_text":"OC","link_flair_background_color":"#0dd3bb",
                   "link_flair_text_color":"light","locked":true,"author_flair_text":"SeraBeeves"}""",
            ).jsonObject,
        )
        assertEquals(0xFF0DD3BB.toInt(), colored.flairColor)
        assertEquals(false, colored.flairDarkText)
        assertTrue(colored.locked)
        assertEquals("SeraBeeves", colored.authorFlair)

        // Unset colors come through as "" (or "transparent"), which means a neutral chip.
        val plain = Parser.post(
            RedditClient.JSON.parseToJsonElement("""{"id":"p3","title":"t","link_flair_background_color":""}""").jsonObject,
        )
        assertEquals(null, plain.flairColor)
        assertTrue(!plain.locked)
    }

    @Test
    fun `api errors surface as exceptions and posted comments parse`() {
        val error = RedditClient.JSON.parseToJsonElement(
            """{"json":{"errors":[["SUBREDDIT_NOEXIST","that community doesn't exist","sr"]]}}""",
        )
        val e = assertFailsWith<RedditException> { Parser.submittedId(error) }
        assertEquals("That community doesn't exist", e.message)

        val reply = Parser.postedComment(DemoWrites.respond("/api/comment", mapOf("thing_id" to "t1_cb1", "text" to "Hi <there>")))
        assertEquals("t1_cb1", reply?.parentId)
        assertEquals("Hi <there>", reply?.body)
        assertTrue(reply!!.bodyHtml.contains("&lt;there&gt;"))
        assertEquals("dm0007", Parser.submittedId(DemoWrites.respond("/api/submit", emptyMap())))
    }

    @Test
    fun `markdown helpers wrap and prefix`() {
        val selected = TextFieldValue("say hello now", TextRange(4, 9))
        assertEquals("say **hello** now", selected.wrap("**").text)
        assertEquals(TextRange(6, 11), selected.wrap("**").selection)

        val cursor = TextFieldValue("ab", TextRange(1))
        assertEquals("a>!!<b", cursor.wrap(">!", "!<").text)
        assertEquals(TextRange(3), cursor.wrap(">!", "!<").selection)

        val lines = TextFieldValue("one\ntwo\nthree", TextRange(5, 10))
        assertEquals("one\n> two\n> three", lines.prefixLines("> ").text)

        val link = TextFieldValue("see docs", TextRange(4, 8)).insertLink()
        assertEquals("see [docs](https://)", link.text)
        assertEquals("https://", link.text.substring(link.selection.start, link.selection.end))
    }

    @Test
    fun `spoilers are detected and settings round-trip`() {
        assertTrue(hasSpoiler("""<p>It was <span class="md-spoiler-text">the butler</span>.</p>"""))
        assertTrue(!hasSpoiler("<p>No secrets here</p>"))

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val custom = Settings(
            theme = ThemeMode.BLACK,
            layout = PostLayout.COMPACT,
            nsfw = NsfwMode.HIDE,
            commentSort = CommentSort.NEW,
            feedLayouts = mapOf("" to PostLayout.SLIDES, "pics" to PostLayout.SMALL_CARDS),
        )
        val text = json.encodeToString(Settings.serializer(), custom)
        assertEquals(custom, json.decodeFromString(Settings.serializer(), text))
        // Each feed keeps its own view; others fall back to the global layout.
        assertEquals(PostLayout.SLIDES, custom.layoutFor(null))
        assertEquals(PostLayout.SMALL_CARDS, custom.layoutFor("Pics"))
        assertEquals(PostLayout.COMPACT, custom.layoutFor("aww"))

        // Swipe stages round-trip, and a damaged list falls back to Sync's defaults.
        val swipes = Settings(postSwipeActions = listOf(SwipeAction.SAVE, SwipeAction.SHARE, SwipeAction.MARK_READ))
        assertEquals(swipes, json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), swipes)))
        assertEquals(listOf(SwipeAction.SAVE, SwipeAction.SHARE, SwipeAction.MARK_READ), swipes.postSwipeStages)
        assertEquals(Settings.DEFAULT_COMMENT_SWIPES, Settings(commentSwipeActions = listOf(SwipeAction.HIDE)).commentSwipeStages)
        // Old or partial blobs load with defaults for anything missing.
        assertEquals(Settings(theme = ThemeMode.DARK), json.decodeFromString(Settings.serializer(), """{"theme":"DARK","removedField":1}"""))
    }

    @Test
    fun `off-reddit links get footnotes and cards`() {
        val text = buildAnnotatedString {
            append("See ")
            withLink(LinkAnnotation.Url("https://i.imgur.com/a.jpg")) { append("this") }
            append(" by ")
            withLink(LinkAnnotation.Url("https://www.reddit.com/u/someone")) { append("u/someone") }
            append(" and ")
            withLink(LinkAnnotation.Url("https://example.com/page")) { append("that") }
            append(", again ")
            withLink(LinkAnnotation.Url("https://i.imgur.com/a.jpg")) { append("this") }
        }
        val result = footnoteLinks(text)
        // Reddit's own links stay plain; a repeated URL keeps its first number.
        assertEquals(listOf("https://i.imgur.com/a.jpg", "https://example.com/page"), result.links)
        assertEquals("See this¹ by u/someone and that², again this¹", result.text.text)
        assertTrue(isImageLink("https://i.imgur.com/a.jpg"))
        assertTrue(isImageLink("https://preview.redd.it/x.jpeg?width=627&format=pjpg"))
        assertTrue(!isImageLink("https://example.com/page"))
    }

    @Test
    fun `selftext previews stop after the first paragraph`() {
        assertEquals("And the winners are...\n…", firstParagraph("And the winners are...\n\ndrumroll\n\n☆ Night Sky"))
        assertEquals("Just one line", firstParagraph("  Just one line \n\n"))
        assertEquals(null, firstParagraph(" \n "))
    }

    @Test
    fun `swipe stages arm at Sync's distances`() {
        val thresholds = listOf(72f, 144f, 216f)
        assertEquals(-1, armedStage(60f, thresholds, 3))
        assertEquals(0, armedStage(80f, thresholds, 3))
        assertEquals(1, armedStage(155f, thresholds, 3))
        assertEquals(2, armedStage(400f, thresholds, 3))
        // With a stage dropped (no Hide on this list), the second is as far as it goes.
        assertEquals(1, armedStage(400f, thresholds, 2))
    }

    @Test
    fun `counts and ages format compactly`() {
        assertEquals("999", Format.count(999))
        assertEquals("1.2k", Format.count(1234))
        assertEquals("5k", Format.count(5021))
        assertEquals("123k", Format.count(123_456))
        assertEquals("1.5m", Format.count(1_500_000))
        assertEquals("now", Format.timeAgo(1000, 1030))
        assertEquals("5h", Format.timeAgo(0, 5 * 3600 + 59))
        assertEquals("2y", Format.timeAgo(0, 2L * 365 * 86_400))
    }
}
