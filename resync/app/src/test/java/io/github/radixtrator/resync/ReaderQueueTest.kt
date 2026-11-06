package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.ui.post.ReaderItem
import io.github.radixtrator.resync.ui.post.readerQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonObject

class ReaderQueueTest {
    private fun comment(id: String, depth: Int, author: String, body: String) = Comment(
        id = id, depth = depth, parentId = "t3_p", fullname = "t1_$id", author = author, body = body, bodyHtml = "",
        score = 1, scoreHidden = false, createdUtc = 0, edited = false, isSubmitter = false, distinguished = null,
        stickied = false, likes = null, flair = null,
    )

    private val plain: (String?, String) -> String = { html, fallback -> html?.takeIf { it.isNotBlank() } ?: fallback }

    @Test
    fun `reads the post, then top-level comments only, skipping deleted ones`() {
        val post = Parser.post(
            RedditClient.JSON.parseToJsonElement("""{"id":"p","title":"Match thread","selftext":"Kickoff at 3"}""").jsonObject,
        )
        val comments = listOf(
            comment("a", 0, "alice", "Goal!"),
            comment("b", 1, "bob", "A reply, not read"),
            comment("c", 0, "[deleted]", "[deleted]"),
            MoreComments(id = "m", depth = 0, parentId = "t3_p", count = 3, children = listOf("x")),
            comment("d", 0, "dave", "  What a save  "),
        )
        assertEquals(
            listOf(
                ReaderItem("Post", "Match thread.\nKickoff at 3"),
                ReaderItem("u/alice", "alice.\nGoal!"),
                ReaderItem("u/dave", "dave.\nWhat a save"),
            ),
            readerQueue(post, comments, plain),
        )
    }

    @Test
    fun `with replies on, replies are read in thread order`() {
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p","title":"Match thread"}""").jsonObject)
        val comments = listOf(
            comment("a", 0, "alice", "Goal!"),
            comment("b", 1, "bob", "Offside"),
            comment("c", 2, "carol", "[removed]"),
            comment("d", 0, "dave", "What a save"),
        )
        assertEquals(
            listOf(
                ReaderItem("Post", "Match thread"),
                ReaderItem("u/alice", "alice.\nGoal!"),
                ReaderItem("u/bob · reply", "bob replies.\nOffside"),
                ReaderItem("u/dave", "dave.\nWhat a save"),
            ),
            readerQueue(post, comments, plain, replies = true),
        )
    }

    @Test
    fun `a link post is just its title`() {
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p","title":"A link"}""").jsonObject)
        assertEquals(listOf(ReaderItem("Post", "A link")), readerQueue(post, emptyList(), plain))
    }
}
