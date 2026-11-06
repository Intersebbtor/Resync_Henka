package io.github.radixtrator.resync.data

/** A link to a community wiki page, e.g. https://www.reddit.com/r/pics/wiki/rules/faq. [page] is "index" for the front page. */
data class WikiLink(val subreddit: String, val page: String)

object Wiki {
    private val LINK = Regex("""^https?://(?:[\w-]+\.)?reddit\.com/r/([\w]+)/(?:wiki|w)(?:/([^?#]*))?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("""<h([1-6])[^>]*>(.*?)</h\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]*>")

    fun parseLink(url: String): WikiLink? {
        val m = LINK.find(url) ?: return null
        val page = m.groupValues[2].trim('/').lowercase().ifEmpty { "index" }
        return WikiLink(m.groupValues[1], page)
    }

    /** A heading in a wiki page: [level] 1-6 and its plain [text]. */
    data class Heading(val level: Int, val text: String)

    /** Reddit's own table of contents, rebuilt from the page's headings. */
    fun headings(html: String): List<Heading> = HEADING.findAll(html).mapNotNull { m ->
        val text = TAG.replace(m.groupValues[2], "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&quot;", "\"").trim()
        text.takeIf { it.isNotEmpty() }?.let { Heading(m.groupValues[1].toInt(), it) }
    }.toList()
}
