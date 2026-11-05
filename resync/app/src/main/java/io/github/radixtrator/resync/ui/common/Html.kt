package io.github.radixtrator.resync.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.em

private const val SPOILER_URL = "resync-spoiler:reveal"
private val PARAGRAPH_BREAK = Regex("""</p>\s*<p>""")
private val SPOILER = Regex("""<span class="md-spoiler-text">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

/**
 * Renders the HTML Reddit generates from markdown (body_html / selftext_html).
 * Links go through LocalUriHandler (the Navigator), so reddit URLs open in-app.
 * Spoilers (`>!text!<`) show as a tappable "[spoiler]" until revealed.
 */
@Composable
fun rememberRedditHtml(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    var revealed by remember(html) { mutableStateOf(false) }
    return remember(html, linkColor, revealed) {
        redditHtml(html, linkColor, revealed) { url ->
            if (url == SPOILER_URL) revealed = true else uriHandler.openUri(url)
        }
    }
}

fun redditHtml(
    html: String,
    linkColor: Color,
    revealSpoilers: Boolean = true,
    onLink: ((String) -> Unit)? = null,
): AnnotatedString {
    // Reddit emits site-relative links (/r/foo, /u/bar) that no URI handler can open as-is.
    var source = html.replace("href=\"/", "href=\"https://www.reddit.com/")
    // fromHtml runs paragraphs together; Reddit (and Sync) leave a blank line between them.
    source = PARAGRAPH_BREAK.replace(source, "</p><br><p>")
    source = SPOILER.replace(source) { m ->
        if (revealSpoilers) "<i>${m.groupValues[1]}</i>" else "<a href=\"$SPOILER_URL\">[spoiler]</a>"
    }
    val listener = onLink?.let { handler ->
        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(handler) }
    }
    val parsed = AnnotatedString.fromHtml(
        source,
        // Sync's "Show larger links": links read a touch bigger than the text around them.
        linkStyles = TextLinkStyles(SpanStyle(color = linkColor, fontSize = 1.1.em)),
        linkInteractionListener = listener,
    )
    return parsed.subSequence(0, parsed.text.trimEnd().length)
}

/** Rendered text plus the links Sync would footnote, in order: link n is marked ⁿ in [text]. */
data class RedditText(val text: AnnotatedString, val links: List<String>)

/**
 * [rememberRedditHtml] with Sync's link footnotes: every link that leaves Reddit gets a superscript
 * number after it, and comes back in [RedditText.links] for [LinkCards] under the text.
 */
@Composable
fun rememberRedditText(html: String, footnotes: Boolean = true): RedditText {
    val text = rememberRedditHtml(html)
    return remember(text, footnotes) { if (footnotes) footnoteLinks(text) else RedditText(text, emptyList()) }
}

/** Numbers the off-Reddit links in [text]; repeats of a URL reuse its number. */
fun footnoteLinks(text: AnnotatedString): RedditText {
    val links = mutableListOf<String>()
    val marks = text.getLinkAnnotations(0, text.length)
        .mapNotNull { range ->
            val url = (range.item as? LinkAnnotation.Url)?.url ?: return@mapNotNull null
            if (!isFootnoted(url)) return@mapNotNull null
            val n = links.indexOf(url).takeIf { it >= 0 } ?: links.size.also { links += url }
            range.end to n + 1
        }
        .sortedBy { it.first }
    if (marks.isEmpty()) return RedditText(text, emptyList())
    val numbered = AnnotatedString.Builder().apply {
        var from = 0
        for ((end, n) in marks) {
            append(text.subSequence(from, end))
            append(superscript(n))
            from = end
        }
        append(text.subSequence(from, text.length))
    }.toAnnotatedString()
    return RedditText(numbered, links)
}

/** Reddit's own pages (users, subs, threads) read fine inline; everything else gets a footnote. */
private fun isFootnoted(url: String): Boolean {
    if (url == SPOILER_URL || !url.startsWith("http")) return false
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
    return !(host == "reddit.com" || host.endsWith(".reddit.com"))
}

private fun superscript(n: Int): String = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

/** True if the HTML has spoiler markup; used by tests and to decide on reveal affordances. */
fun hasSpoiler(html: String) = SPOILER.containsMatchIn(html)
