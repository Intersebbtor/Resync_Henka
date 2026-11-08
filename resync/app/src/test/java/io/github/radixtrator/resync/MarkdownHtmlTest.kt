package io.github.radixtrator.resync

import io.github.radixtrator.resync.ui.common.RULE
import io.github.radixtrator.resync.ui.common.hasSpoiler
import io.github.radixtrator.resync.ui.common.redditMarkup
import io.github.radixtrator.resync.ui.compose.draftKey
import io.github.radixtrator.resync.ui.compose.draftLabel
import io.github.radixtrator.resync.ui.compose.markdownToHtml
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownHtmlTest {
    @Test
    fun `inline formatting`() {
        assertEquals(
            "<p>Hello <strong>bold</strong> and <em>it</em> and <del>gone</del> and <code>co*de*</code></p>",
            markdownToHtml("Hello **bold** and *it* and ~~gone~~ and `co*de*`"),
        )
        assertEquals("<p><strong><em>both</em></strong> snake_case_name <strong>strong</strong> <em>under</em></p>", markdownToHtml("***both*** snake_case_name __strong__ _under_"))
        assertEquals("<p><strong>a <em>b</em> c</strong></p>", markdownToHtml("**a *b* c**"))
        assertEquals("<p>unclosed **bold and *it</p>", markdownToHtml("unclosed **bold and *it"))
        assertEquals("<p>2 * 3 * 4</p>", markdownToHtml("2 * 3 * 4"))
        assertEquals("<p><sup>two words</sup> <sup>up</sup> x</p>", markdownToHtml("^(two words) ^up x"))
        assertEquals("<p><code>a `b` c</code></p>", markdownToHtml("`` a `b` c ``"))
    }

    @Test
    fun `text is escaped but entities and backslash escapes work`() {
        assertEquals("<p>a &amp; b &lt; c &nbsp; *not* &lt;b&gt;</p>", markdownToHtml("a & b < c &nbsp; \\*not\\* <b>"))
    }

    @Test
    fun `spoilers use Reddit's markup`() {
        val html = markdownToHtml(">!secret **stuff**!< text")
        assertEquals("<p><span class=\"md-spoiler-text\">secret <strong>stuff</strong></span> text</p>", html)
        assertTrue(hasSpoiler(html))
        assertEquals("<p><a href=\"resync-spoiler:reveal\">[spoiler]</a> text</p>", redditMarkup(html, revealSpoilers = false))
        // Unclosed, it's just text.
        assertEquals("<p>&gt;!nope</p>", markdownToHtml(">!nope"))
    }

    @Test
    fun `links and autolinks`() {
        assertEquals(
            "<p>see <a href=\"https://x.com/a?b=1&amp;c=2\">the <em>link</em></a> and <a href=\"https://example.com/foo\">https://example.com/foo</a>.</p>",
            markdownToHtml("see [the *link*](https://x.com/a?b=1&c=2) and https://example.com/foo."),
        )
        assertEquals("<p><a href=\"/r/pics\">r/pics</a> and <a href=\"/u/spez\">/u/spez</a>, not foo/r/bar</p>", markdownToHtml("r/pics and /u/spez, not foo/r/bar"))
        assertEquals("<p><a href=\"https://a.com\">https://a.com</a></p>", markdownToHtml("[https://a.com](https://a.com \"title\")"))
        assertEquals(
            "<p>(see <a href=\"https://en.wikipedia.org/wiki/Foo_(bar)\">https://en.wikipedia.org/wiki/Foo_(bar)</a>)</p>",
            markdownToHtml("(see https://en.wikipedia.org/wiki/Foo_(bar))"),
        )
        // Relative links go to reddit.com once rendered.
        assertTrue("href=\"https://www.reddit.com/r/pics\"" in redditMarkup(markdownToHtml("r/pics"), revealSpoilers = true))
    }

    @Test
    fun `paragraphs, line breaks and headings`() {
        assertEquals("<p>a\nb<br/>\nc</p>\n\n<p>d</p>", markdownToHtml("a\nb  \nc\n\n\nd"))
        assertEquals("<p>a<br/>\nb</p>", markdownToHtml("a\\\nb"))
        assertEquals("<h1>Title</h1>\n\n<p>para</p>", markdownToHtml("# Title\npara"))
        assertEquals("<h3>C#</h3>", markdownToHtml("### C# ###"))
        assertEquals("<p>#hashtag</p>", markdownToHtml("#hashtag"))
        assertEquals("<h1>Big</h1>\n\n<h2>Small</h2>", markdownToHtml("Big\n===\n\nSmall\n---"))
    }

    @Test
    fun `rules, quotes and code`() {
        assertEquals("<p>a</p>\n\n<hr/>\n\n<p>b</p>", markdownToHtml("a\n\n---\n\nb"))
        assertEquals("<p>a</p>\n\n<hr/>", markdownToHtml("a\n\n* * *"))
        assertEquals("<p>a</p><br><p>$RULE</p>", redditMarkup(markdownToHtml("a\n\n***"), revealSpoilers = true))
        assertEquals(
            "<blockquote>\n<p>quoted\nmore</p>\n\n<blockquote>\n<p>nested</p>\n</blockquote>\n</blockquote>\n\n<p>after</p>",
            markdownToHtml("> quoted\nmore\n>\n>> nested\n\nafter"),
        )
        assertEquals("<pre><code>code &lt;x&gt;\n\n  more\n</code></pre>\n\n<p>text</p>", markdownToHtml("    code <x>\n\n      more\n\ntext"))
        assertEquals("<pre><code>fenced *x*\n</code></pre>\n\n<p>y</p>", markdownToHtml("```kotlin\nfenced *x*\n```\ny"))
    }

    @Test
    fun `lists render the way redditHtml numbers them`() {
        assertEquals("<ul>\n<li>one</li>\n<li>two\n\n<ul>\n<li>nested</li>\n</ul></li>\n<li>three</li>\n</ul>", markdownToHtml("- one\n- two\n  - nested\n* three"))
        assertEquals("<ol start=\"3\">\n<li>c</li>\n<li>d</li>\n</ol>", markdownToHtml("3. c\n4. d"))
        assertEquals("<ol>\n<li><p>a</p></li>\n<li><p>b</p></li>\n</ol>", markdownToHtml("1. a\n\n2. b"))
        assertEquals("<p>para</p>\n\n<ul>\n<li>right after</li>\n</ul>", markdownToHtml("para\n- right after"))
        assertEquals("<ul>\n<li>a\nlazy</li>\n</ul>\n\n<p>para</p>", markdownToHtml("- a\nlazy\n\npara"))
        // A year starting a line doesn't interrupt a paragraph.
        assertEquals("<p>In\n2015. was a year</p>", markdownToHtml("In\n2015. was a year"))

        assertEquals("<p>3. c<br>4. d</p>", redditMarkup(markdownToHtml("3. c\n4. d"), revealSpoilers = true))
        assertEquals("<p>• one<br>• two<br>• nested</p>", redditMarkup(markdownToHtml("- one\n- two\n  - nested"), revealSpoilers = true))
    }

    @Test
    fun `draft keys and labels name the composer`() {
        assertEquals("reply:t1_abc", ComposeRoute(ComposeMode.REPLY, target = "t1_abc").draftKey())
        assertEquals("edit:t3_x", ComposeRoute(ComposeMode.EDIT, target = "t3_x").draftKey())
        assertEquals("message:spez", ComposeRoute(ComposeMode.MESSAGE, to = "/u/Spez").draftKey())
        assertEquals("message:r/pics", ComposeRoute(ComposeMode.MESSAGE, to = "/r/pics").draftKey())

        assertEquals("Reply to \"Nice post\"", ComposeRoute(ComposeMode.REPLY, target = "t1_abc", quote = "\nNice post\nmore").draftLabel())
        assertEquals("Reply", ComposeRoute(ComposeMode.REPLY, target = "t1_abc").draftLabel())
        assertEquals("Message to u/spez", ComposeRoute(ComposeMode.MESSAGE).draftLabel("spez"))
        assertEquals("Message to r/pics", ComposeRoute(ComposeMode.MESSAGE, to = "/r/pics").draftLabel())
        assertEquals("Message", ComposeRoute(ComposeMode.MESSAGE).draftLabel())
    }
}
