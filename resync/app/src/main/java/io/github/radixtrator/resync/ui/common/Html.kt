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

private const val SPOILER_URL = "resync-spoiler:reveal"
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
    source = SPOILER.replace(source) { m ->
        if (revealSpoilers) "<i>${m.groupValues[1]}</i>" else "<a href=\"$SPOILER_URL\">[spoiler]</a>"
    }
    val listener = onLink?.let { handler ->
        LinkInteractionListener { link -> (link as? LinkAnnotation.Url)?.url?.let(handler) }
    }
    val parsed = AnnotatedString.fromHtml(
        source,
        linkStyles = TextLinkStyles(SpanStyle(color = linkColor)),
        linkInteractionListener = listener,
    )
    return parsed.subSequence(0, parsed.text.trimEnd().length)
}

/** True if the HTML has spoiler markup; used by tests and to decide on reveal affordances. */
fun hasSpoiler(html: String) = SPOILER.containsMatchIn(html)
