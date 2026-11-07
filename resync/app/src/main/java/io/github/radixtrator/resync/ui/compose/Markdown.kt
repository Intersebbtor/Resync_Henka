package io.github.radixtrator.resync.ui.compose

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Draft
import io.github.radixtrator.resync.data.DraftStore
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Numbers every line the selection touches, "1. a", "2. b". */
fun TextFieldValue.numberLines(): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0)).let { if (selection.min == 0 || it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', selection.max).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    val numbered = block.split('\n').mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n")
    return copy(
        text = text.substring(0, lineStart) + numbered + text.substring(lineEnd),
        selection = TextRange(selection.min + 3, selection.max + numbered.length - block.length),
    )
}

/** Inserts [snippet] at the cursor (replacing the selection), leaving the cursor after it. */
fun TextFieldValue.insert(snippet: String): TextFieldValue =
    copy(text = text.substring(0, selection.min) + snippet + text.substring(selection.max), selection = TextRange(selection.min + snippet.length))

/** Inserts a link to an uploaded image at the cursor, labelled with the selection or "image". */
fun TextFieldValue.insertImageLink(url: String): TextFieldValue =
    insert("[" + text.substring(selection.min, selection.max).ifEmpty { "image" } + "](" + url + ")")

/** The composer's image tool: [pick] opens the system picker; [uploading] while the picked image goes up. */
class ComposerImageUpload(val uploading: Boolean, val pick: () -> Unit)

/**
 * Picks an image, uploads it to Reddit's media storage and hands its hosted URL to [onUploaded].
 * A failure shows a toast. The upload is cancelled if the composer leaves the screen.
 */
@Composable
fun rememberComposerImageUpload(container: AppContainer, onUploaded: (String) -> Unit): ComposerImageUpload {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnUploaded = rememberUpdatedState(onUploaded)
    var uploading by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !uploading) {
            uploading = true
            scope.launch {
                try {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                        ?: error("Couldn't read that image")
                    val ext = mime.substringAfter('/', "jpg").substringBefore(';').let { if (it == "jpeg") "jpg" else it }
                    val url = container.client.uploadImage("image.$ext", mime, bytes)
                    latestOnUploaded.value(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't upload the image: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
                } finally {
                    uploading = false
                }
            }
        }
    }
    return ComposerImageUpload(uploading) { launcher.launch("image/*") }
}

private val EMOJI = listOf("😀", "😂", "😍", "😢", "😡", "👍", "👎", "🙏", "🔥", "🎉", "❤️", "🤔")

/**
 * Sync's formatting bar. Preview shows the text as it will look once posted; Drafts (given [drafts]) lists
 * other composers' unsent text to insert here, leaving out this composer's own ([draftKey]). Image (given
 * [imageUpload], see [rememberComposerImageUpload]) uploads a picked image and links it; Info shows a markdown cheat sheet.
 */
@Composable
fun MarkdownToolbar(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    drafts: DraftStore? = null,
    draftKey: String? = null,
    imageUpload: ComposerImageUpload? = null,
) {
    var previewing by remember { mutableStateOf(false) }
    var pickingDraft by remember { mutableStateOf(false) }
    var showingInfo by remember { mutableStateOf(false) }
    if (previewing) MarkdownPreviewDialog(value.text, onDismiss = { previewing = false })
    if (showingInfo) MarkdownInfoDialog(onDismiss = { showingInfo = false })
    if (pickingDraft && drafts != null) {
        DraftsDialog(
            drafts = drafts,
            exclude = draftKey,
            onPick = { draft ->
                onChange(value.insert(draft.text))
                pickingDraft = false
            },
            onDismiss = { pickingDraft = false },
        )
    }
    Row(modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { previewing = true }) { Text("Preview") }
        if (drafts != null) TextButton(onClick = { pickingDraft = true }) { Text("Drafts") }
        if (imageUpload != null) {
            TextButton(onClick = imageUpload.pick, enabled = !imageUpload.uploading) {
                if (imageUpload.uploading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Image")
                }
            }
        }
        TextButton(onClick = { onChange(value.wrap("**")) }) { Text("B", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { onChange(value.wrap("*")) }) { Text("I", fontStyle = FontStyle.Italic) }
        TextButton(onClick = { onChange(value.wrap("~~")) }) { Text("S", textDecoration = TextDecoration.LineThrough) }
        TextButton(onClick = { onChange(value.insertLink()) }) { Text("Link") }
        TextButton(onClick = { onChange(value.prefixLines("> ")) }) { Text("Quote") }
        TextButton(onClick = { onChange(value.wrap("`")) }) { Text("Code") }
        TextButton(onClick = { onChange(value.wrap(">!", "!<")) }) { Text("Spoiler") }
        TextButton(onClick = { onChange(value.prefixLines("- ")) }) { Text("List") }
        TextButton(onClick = { onChange(value.wrap("^(", ")")) }) { Text("Sup") }
        TextButton(onClick = { onChange(value.numberLines()) }) { Text("1.") }
        TextButton(onClick = { onChange(value.prefixLines("# ")) }) { Text("H") }
        TextButton(onClick = { onChange(value.prefixLines("    ")) }) { Text("Block") }
        TextButton(onClick = { onChange(value.insert("\n\n---\n\n")) }) { Text("Rule") }
        var emojiOpen by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { emojiOpen = true }) { Text("😀") }
            DropdownMenu(expanded = emojiOpen, onDismissRequest = { emojiOpen = false }) {
                EMOJI.chunked(4).forEach { row ->
                    Row {
                        row.forEach { e -> TextButton(onClick = { emojiOpen = false; onChange(value.insert(e)) }) { Text(e) } }
                    }
                }
            }
        }
        TextButton(onClick = { showingInfo = true }) { Text("Info") }
    }
}

private val CHEAT_SHEET = listOf(
    "**bold**" to "bold",
    "*italic*" to "italic",
    "~~strikethrough~~" to "strikethrough",
    ">!spoiler!<" to "spoiler",
    "^(superscript)" to "superscript",
    "`code`" to "inline code",
    "[text](https://…)" to "link",
    "> quote" to "quote",
    "- item" to "bulleted list",
    "1. item" to "numbered list",
    "# Heading" to "heading",
    "    code" to "code block (4 spaces)",
    "---" to "horizontal rule",
    "r/sub, u/name" to "community and user links",
    "a | b\n--|--\n1 | 2" to "table",
    "two spaces at line end" to "line break; a blank line starts a paragraph",
)

/** Sync's composer info: a quick reference of Reddit's markdown. */
@Composable
private fun MarkdownInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formatting help") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CHEAT_SHEET.forEach { (syntax, meaning) ->
                    Column {
                        Text(syntax, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        Text(meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The markdown rendered the way posted comments are. */
@Composable
private fun MarkdownPreviewDialog(markdown: String, onDismiss: () -> Unit) {
    val html = remember(markdown) { markdownToHtml(markdown) }
    val rendered = rememberRedditHtml(html)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (markdown.isBlank()) {
                    Text("Nothing to preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    RedditBodyText(rendered, MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Saved drafts, newest first: tap one to insert it, or delete it. */
@Composable
private fun DraftsDialog(drafts: DraftStore, exclude: String?, onPick: (Draft) -> Unit, onDismiss: () -> Unit) {
    val all by drafts.drafts.collectAsStateWithLifecycle()
    val shown = all.filter { it.key != exclude }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drafts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shown.isEmpty()) Text("No saved drafts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                shown.forEach { draft ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(draft) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                draft.label.ifBlank { "Draft" } + " · " + Format.timeAgo(draft.savedAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(draft.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { drafts.clear(draft.key) }) { Icon(Icons.Default.Delete, "Delete draft") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
