package io.github.radixtrator.resync.ui.compose

/*
 * The composer's preview: markdown in, HTML shaped like Reddit's body_html out, so it renders through
 * redditHtml exactly like a posted comment. It covers what people commonly write on Reddit (paragraphs,
 * emphasis, strikethrough, code, quotes, lists, headings, links, superscript, spoilers, rules), not every
 * corner of Reddit's markdown: no tables, and unusual nestings may come out differently.
 */

private val FENCE = Regex("""^ {0,3}(```|~~~)""")
private val HEADING = Regex("""^ {0,3}(#{1,6})(?:[ ]+|$)(.*?)(?:[ ]+#+)?[ ]*$""")
private val SETEXT = Regex("""^ {0,3}(=+|-+)[ ]*$""")
private val RULE = Regex("""^ {0,3}([-*_])(?:[ ]*\1){2,}[ ]*$""")
// ">!" opens a spoiler, not a quote.
private val QUOTE = Regex("""^ {0,3}>(?!!)""")
private val QUOTE_PREFIX = Regex("""^ {0,3}> ?""")
private val BULLET = Regex("""^( {0,3})([-*+])[ ]+(.*)$""")
private val ORDERED = Regex("""^( {0,3})(\d{1,9})[.)][ ]+(.*)$""")
private val ENTITY = Regex("""&(?:#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});""")
private val COMMUNITY_LINK = Regex("""/?[ru]/[A-Za-z0-9_][A-Za-z0-9_-]*""")
private const val ESCAPABLE = "\\`*_{}[]()#+-.!<>~^|\"'&$%,/:;=?@"
private const val HARD_BREAK = '\u0001'

fun markdownToHtml(markdown: String): String {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split('\n')
    return Blocks(lines).render()
}

private fun escapeHtml(text: String): String = buildString(text.length) { text.forEach { append(escapeHtml(it)) } }

private fun escapeHtml(c: Char): String = when (c) {
    '&' -> "&amp;"
    '<' -> "&lt;"
    '>' -> "&gt;"
    '"' -> "&quot;"
    else -> c.toString()
}

/** What this regex matches starting exactly at [start] of [text], if anything. */
private fun Regex.prefixAt(text: String, start: Int): String? {
    val matcher = toPattern().matcher(text).region(start, text.length)
    return if (matcher.lookingAt()) matcher.group() else null
}

private fun indentOf(line: String) = line.length - line.trimStart().length

/** True if [line] ends a paragraph by starting a block of its own. */
private fun startsBlock(line: String): Boolean =
    FENCE.containsMatchIn(line) || RULE.matches(line) || HEADING.matches(line) || QUOTE.containsMatchIn(line) ||
        BULLET.matches(line) || ORDERED.matchEntire(line)?.groupValues?.get(2) == "1"

private class Blocks(private val lines: List<String>) {
    private var i = 0
    private val out = mutableListOf<String>()

    fun render(): String {
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                FENCE.containsMatchIn(line) -> fenced()
                line.startsWith("    ") -> indentedCode()
                RULE.matches(line) -> {
                    out += "<hr/>"
                    i++
                }
                HEADING.matches(line) -> heading()
                QUOTE.containsMatchIn(line) -> quote()
                BULLET.matches(line) || ORDERED.matches(line) -> list()
                else -> paragraph()
            }
        }
        return out.joinToString("\n\n")
    }

    private fun fenced() {
        val fence = FENCE.find(lines[i])!!.groupValues[1]
        i++
        val body = mutableListOf<String>()
        while (i < lines.size && !lines[i].trimStart().startsWith(fence)) body += lines[i++]
        i++ // the closing fence, if there is one
        out += "<pre><code>" + escapeHtml(body.joinToString("\n")) + "\n</code></pre>"
    }

    private fun indentedCode() {
        val body = mutableListOf<String>()
        while (i < lines.size && (lines[i].startsWith("    ") || lines[i].isBlank())) {
            body += if (lines[i].isBlank()) "" else lines[i].substring(4)
            i++
        }
        while (body.isNotEmpty() && body[body.lastIndex].isEmpty()) body.removeAt(body.lastIndex)
        out += "<pre><code>" + escapeHtml(body.joinToString("\n")) + "\n</code></pre>"
    }

    private fun heading() {
        val m = HEADING.matchEntire(lines[i++])!!
        val level = m.groupValues[1].length
        out += "<h$level>" + Inline.render(m.groupValues[2]) + "</h$level>"
    }

    private fun quote() {
        val body = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            body += when {
                QUOTE.containsMatchIn(line) -> QUOTE_PREFIX.replaceFirst(line, "")
                // A line straight after quoted text carries on its paragraph, as in Reddit.
                line.isNotBlank() && body.lastOrNull()?.isNotBlank() == true && !startsBlock(line) -> line
                else -> break
            }
            i++
        }
        out += "<blockquote>\n" + Blocks(body).render() + "\n</blockquote>"
    }

    private fun list() {
        val ordered = ORDERED.matches(lines[i])
        val pattern = if (ordered) ORDERED else BULLET
        val first = pattern.matchEntire(lines[i])!!
        val start = if (ordered) first.groupValues[2].toInt() else 1
        val items = mutableListOf<MutableList<String>>()
        var contentIndent = 0
        var loose = false
        var blankBefore = false
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                blankBefore = true
                i++
                continue
            }
            val indent = indentOf(line)
            val item = pattern.matchEntire(line)
            when {
                item != null && (items.isEmpty() || indent < contentIndent) -> {
                    if (items.isNotEmpty() && blankBefore) loose = true
                    val content = item.groupValues[3]
                    contentIndent = line.length - content.length
                    items.add(mutableListOf(content))
                }
                // Indented lines belong to the item: more paragraphs, or a nested list.
                indent >= minOf(contentIndent, 2) -> {
                    if (blankBefore) {
                        loose = true
                        items.last().add("")
                    }
                    items.last().add(line.substring(minOf(indent, contentIndent)))
                }
                !blankBefore && !startsBlock(line) -> items.last().add(line.trimStart())
                else -> break
            }
            blankBefore = false
            i++
        }
        val tag = if (ordered) "ol" else "ul"
        val open = if (ordered && start != 1) "<ol start=\"$start\">" else "<$tag>"
        val rendered = items.map { body ->
            var html = Blocks(body).render()
            // Tight lists hold their text bare, as Reddit writes them.
            if (!loose && html.startsWith("<p>")) {
                val end = html.indexOf("</p>")
                html = html.substring(3, end) + html.substring(end + 4)
            }
            "<li>$html</li>"
        }
        out += open + "\n" + rendered.joinToString("\n") + "\n</$tag>"
    }

    private fun paragraph() {
        val body = mutableListOf(lines[i++])
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            val underline = SETEXT.matchEntire(line)
            if (underline != null) {
                i++
                val level = if (underline.groupValues[1].startsWith("=")) 1 else 2
                out += "<h$level>" + Inline.render(joinLines(body)) + "</h$level>"
                return
            }
            if (startsBlock(line)) break
            body += line
            i++
        }
        out += "<p>" + Inline.render(joinLines(body)) + "</p>"
    }

    /** A paragraph's lines as one string; two trailing spaces or a trailing backslash break the line. */
    private fun joinLines(body: List<String>): String = body.mapIndexed { n, raw ->
        val line = raw.trimStart()
        when {
            n == body.lastIndex -> line.trimEnd()
            line.endsWith("  ") -> line.trimEnd() + HARD_BREAK
            line.endsWith("\\") && !line.endsWith("\\\\") -> line.dropLast(1) + HARD_BREAK
            else -> line.trimEnd()
        }
    }.joinToString("\n")
}

private class Inline(private val s: String, private val autolink: Boolean) {
    private val out = StringBuilder()

    companion object {
        fun render(text: String, autolink: Boolean = true): String = Inline(text, autolink).html()
    }

    fun html(): String {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = when (c) {
                '\\' -> escaped(i)
                HARD_BREAK -> {
                    out.append("<br/>")
                    i + 1
                }
                '`' -> code(i)
                '>' -> spoiler(i)
                '!' -> if (s.startsWith("![", i)) link(i + 1) else null
                '[' -> link(i)
                '~' -> strike(i)
                '*', '_' -> emphasis(i)
                '^' -> superscript(i)
                '&' -> entity(i)
                'h' -> url(i)
                'r', 'u', '/' -> community(i)
                else -> null
            }
            if (next != null) {
                i = next
            } else {
                out.append(escapeHtml(c))
                i++
            }
        }
        return out.toString()
    }

    private fun escaped(i: Int): Int? {
        if (i + 1 >= s.length || s[i + 1] !in ESCAPABLE) return null
        out.append(escapeHtml(s[i + 1]))
        return i + 2
    }

    private fun runLength(at: Int, c: Char): Int {
        var n = 0
        while (at + n < s.length && s[at + n] == c) n++
        return n
    }

    private fun code(i: Int): Int {
        val n = runLength(i, '`')
        var j = s.indexOf('`', i + n)
        while (j >= 0) {
            val run = runLength(j, '`')
            if (run == n) {
                var content = s.substring(i + n, j).replace('\n', ' ')
                if (content.length > 2 && content.startsWith(" ") && content.endsWith(" ") && content.isNotBlank()) {
                    content = content.substring(1, content.length - 1)
                }
                out.append("<code>").append(escapeHtml(content)).append("</code>")
                return j + n
            }
            j = s.indexOf('`', j + run)
        }
        // No closing run: the backticks are just text.
        out.append("`".repeat(n))
        return i + n
    }

    private fun spoiler(i: Int): Int? {
        if (!s.startsWith(">!", i)) return null
        val end = s.indexOf("!<", i + 2)
        if (end <= i + 2) return null
        out.append("<span class=\"md-spoiler-text\">").append(render(s.substring(i + 2, end), autolink)).append("</span>")
        return end + 2
    }

    /** `[text](url)` starting at the `[` at [i]; images (`![alt](url)`) come here too and become links. */
    private fun link(i: Int): Int? {
        val close = matching(i, '[', ']') ?: return null
        if (close + 1 >= s.length || s[close + 1] != '(') return null
        val end = matching(close + 1, '(', ')') ?: return null
        var target = s.substring(close + 2, end).trim()
        target = target.split(' ', '\n').first().removePrefix("<").removeSuffix(">")
        if (target.isEmpty()) return null
        val label = s.substring(i + 1, close).ifBlank { target }
        out.append("<a href=\"").append(escapeHtml(target)).append("\">").append(render(label, autolink = false)).append("</a>")
        return end + 1
    }

    /** The index of the [closeChar] that balances the [openChar] at [i], skipping backslash escapes. */
    private fun matching(i: Int, openChar: Char, closeChar: Char): Int? {
        var depth = 0
        var j = i
        while (j < s.length) {
            when (s[j]) {
                '\\' -> j++
                openChar -> depth++
                closeChar -> if (--depth == 0) return j
            }
            j++
        }
        return null
    }

    private fun strike(i: Int): Int? {
        if (!s.startsWith("~~", i) || i + 2 >= s.length || s[i + 2].isWhitespace()) return null
        val end = s.indexOf("~~", i + 3)
        if (end < 0) return null
        out.append("<del>").append(render(s.substring(i + 2, end), autolink)).append("</del>")
        return end + 2
    }

    private fun emphasis(i: Int): Int? {
        val c = s[i]
        // snake_case isn't emphasis.
        if (c == '_' && i > 0 && s[i - 1].isLetterOrDigit()) return null
        fun closes(after: Int) = c == '*' || after >= s.length || !s[after].isLetterOrDigit()

        if (s.startsWith("$c$c", i) && i + 2 < s.length && !s[i + 2].isWhitespace()) {
            val open = i + 2
            var j = s.indexOf("$c$c", open + 1)
            while (j >= 0) {
                // In a longer run ("***x***") the closing pair is the last two.
                var k = j
                while (k + 2 < s.length && s[k + 2] == c) k++
                if (!s[k - 1].isWhitespace() && closes(k + 2)) {
                    out.append("<strong>").append(render(s.substring(open, k), autolink)).append("</strong>")
                    return k + 2
                }
                j = s.indexOf("$c$c", k + 2)
            }
        }
        val open = i + 1
        if (open >= s.length || s[open].isWhitespace()) return null
        var j = open + 1
        while (j < s.length) {
            if (s[j] == c) {
                val run = runLength(j, c)
                if (run == 1 && !s[j - 1].isWhitespace() && closes(j + 1)) {
                    out.append("<em>").append(render(s.substring(open, j), autolink)).append("</em>")
                    return j + 1
                }
                j += run // a "**" inside belongs to bold text
            } else {
                j++
            }
        }
        return null
    }

    /** `^(several words)` or `^word`. */
    private fun superscript(i: Int): Int? {
        if (i + 1 >= s.length) return null
        if (s[i + 1] == '(') {
            val end = matching(i + 1, '(', ')') ?: return null
            if (end == i + 2) return null
            out.append("<sup>").append(render(s.substring(i + 2, end), autolink)).append("</sup>")
            return end + 1
        }
        var end = i + 1
        while (end < s.length && !s[end].isWhitespace() && s[end] != HARD_BREAK) end++
        if (end == i + 1) return null
        out.append("<sup>").append(render(s.substring(i + 1, end), autolink)).append("</sup>")
        return end
    }

    /** HTML entities (`&nbsp;`, `&#8203;`) pass through, as Reddit lets them. */
    private fun entity(i: Int): Int? {
        val entity = ENTITY.prefixAt(s, i) ?: return null
        out.append(entity)
        return i + entity.length
    }

    private fun startsWord(i: Int) = i == 0 || !(s[i - 1].isLetterOrDigit() || s[i - 1] == '/' || s[i - 1] == '_')

    private fun url(i: Int): Int? {
        if (!autolink || !startsWord(i) || !(s.startsWith("http://", i) || s.startsWith("https://", i))) return null
        var end = i
        while (end < s.length && !s[end].isWhitespace() && s[end] != '<' && s[end] != HARD_BREAK) end++
        // Sentence punctuation after a URL isn't part of it, nor is a ")" closing a parenthesis around it.
        while (end > i) {
            val last = s[end - 1]
            val url = s.substring(i, end)
            if (last in ".,;:!?\"'*_~" || (last == ')' && url.count { it == '(' } < url.count { it == ')' })) end-- else break
        }
        val url = s.substring(i, end)
        if (url.substringAfter("://").isEmpty()) return null
        out.append("<a href=\"").append(escapeHtml(url)).append("\">").append(escapeHtml(url)).append("</a>")
        return end
    }

    /** r/name and u/name link themselves, as on Reddit. */
    private fun community(i: Int): Int? {
        if (!autolink || !startsWord(i)) return null
        val name = COMMUNITY_LINK.prefixAt(s, i) ?: return null
        val path = if (name.startsWith("/")) name else "/$name"
        out.append("<a href=\"").append(path).append("\">").append(name).append("</a>")
        return i + name.length
    }
}
