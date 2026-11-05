package io.github.radixtrator.resync.ui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration

/** Wraps the selection in [prefix]/[suffix]; with no selection, leaves the cursor between them. */
fun TextFieldValue.wrap(prefix: String, suffix: String = prefix): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val text = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end)
    val selection = if (start == end) TextRange(start + prefix.length) else TextRange(start + prefix.length, end + prefix.length)
    return copy(text = text, selection = selection)
}

/** Adds [prefix] to the start of every line the selection touches (quotes, lists). */
fun TextFieldValue.prefixLines(prefix: String): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0)).let { if (selection.min == 0 || it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', selection.max).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    val prefixed = block.split('\n').joinToString("\n") { prefix + it }
    val added = prefixed.length - block.length
    return copy(
        text = text.substring(0, lineStart) + prefixed + text.substring(lineEnd),
        selection = TextRange(selection.min + prefix.length, selection.max + added),
    )
}

/** Inserts a markdown link around the selection (or a placeholder label) with the URL selected. */
fun TextFieldValue.insertLink(): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val label = text.substring(start, end).ifEmpty { "text" }
    val url = "https://"
    val inserted = "[$label]($url)"
    val urlStart = start + label.length + 3
    return copy(
        text = text.substring(0, start) + inserted + text.substring(end),
        selection = TextRange(urlStart, urlStart + url.length),
    )
}

@Composable
fun MarkdownToolbar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { onChange(value.wrap("**")) }) { Text("B", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { onChange(value.wrap("*")) }) { Text("I", fontStyle = FontStyle.Italic) }
        TextButton(onClick = { onChange(value.wrap("~~")) }) { Text("S", textDecoration = TextDecoration.LineThrough) }
        TextButton(onClick = { onChange(value.insertLink()) }) { Text("Link") }
        TextButton(onClick = { onChange(value.prefixLines("> ")) }) { Text("Quote") }
        TextButton(onClick = { onChange(value.wrap("`")) }) { Text("Code") }
        TextButton(onClick = { onChange(value.wrap(">!", "!<")) }) { Text("Spoiler") }
        TextButton(onClick = { onChange(value.prefixLines("- ")) }) { Text("List") }
        TextButton(onClick = { onChange(value.wrap("^(", ")")) }) { Text("Sup") }
    }
}
