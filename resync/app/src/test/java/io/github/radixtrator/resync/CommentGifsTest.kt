package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.CommentGif
import io.github.radixtrator.resync.ui.common.gifBody
import io.github.radixtrator.resync.ui.common.gifUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentGifsTest {
    @Test
    fun `gif links resolve to the animated image`() {
        assertEquals("https://i.giphy.com/abc123.gif", gifUrl("https://giphy.com/gifs/funny-cat-abc123"))
        assertEquals("https://i.giphy.com/abc123.gif", gifUrl("https://media.giphy.com/media/abc123/giphy.gif"))
        assertEquals("https://i.imgur.com/xyz.gif", gifUrl("https://i.imgur.com/xyz.gifv"))
        assertEquals("https://i.redd.it/up.gif", gifUrl("https://preview.redd.it/up.gif?width=200&format=png8"))
        assertEquals("https://example.com/a.gif", gifUrl("https://example.com/a.gif"))
        assertNull(gifUrl("https://example.com/a.png"))
        assertNull(gifUrl("https://giphy.com/explore/cats"))
    }

    @Test
    fun `giphy picks leave the text and become previews`() {
        val gif = CommentGif("giphy|abc123", "https://external-preview.redd.it/x.gif?s=1", 200, 150, "https://giphy.com/gifs/abc123")
        val html = """<div class="md"><p>lol</p><p><a href="https://giphy.com/gifs/abc123" target="_blank"><img src="https://external-preview.redd.it/x.gif?s=1"></a></p></div>"""
        val split = gifBody(html, listOf(gif))
        assertEquals("""<div class="md"><p>lol</p></div>""", split.html)
        assertEquals(listOf(gif), split.gifs)
    }

    @Test
    fun `bare gif links are swapped for previews, worded ones kept`() {
        val html = """<p><a href="https://i.imgur.com/a.gifv">https://i.imgur.com/a.gifv</a> and <a href="https://i.imgur.com/b.gif">this</a></p>"""
        val split = gifBody(html, emptyList())
        assertEquals("""<p> and <a href="https://i.imgur.com/b.gif">this</a></p>""", split.html)
        assertEquals(listOf("https://i.imgur.com/a.gif", "https://i.imgur.com/b.gif"), split.gifs.map { it.url })
    }
}
