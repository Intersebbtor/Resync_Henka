package io.github.radixtrator.resync.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.ReportReason
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sync's "Report reasons": the community's rules for [comment]s or posts, Reddit's own rules, then Other.
 * Each asks before sending, since a report goes to the moderators (or Reddit) for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(fullname: String, subreddit: String, comment: Boolean, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val reasons by produceState<Result<List<ReportReason>>?>(null, subreddit, comment) {
        value = runCatching { container.api.reportReasons(subreddit, comment) }.onFailure { if (it is CancellationException) throw it }
    }
    var confirm by remember { mutableStateOf<ReportReason?>(null) }
    var other by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetTitle("Report reasons", topPadding = 0.dp)
            when (val result = reasons) {
                null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Without the rules (a private community, say), Other still works.
                    result.onFailure {
                        Text(
                            "Couldn't load r/$subreddit's rules: ${it.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    result.getOrDefault(emptyList()).forEach { reason -> OptionRow(reason.text, null, onClick = { confirm = reason }) }
                    OptionRow("Other", null, onClick = { other = true })
                }
            }
        }
    }
    confirm?.let { reason ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Report this ${if (comment) "comment" else "post"}?") },
            text = { Text(reason.text) },
            confirmButton = {
                TextButton(onClick = {
                    container.report(fullname, reason)
                    onDismiss()
                }) { Text("Report") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    if (other) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { other = false },
            title = { Text("Report this ${if (comment) "comment" else "post"}?") },
            text = { OutlinedTextField(text, { text = it.take(100) }, label = { Text("Reason") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    container.report(fullname, reason = null, other = text.trim())
                    onDismiss()
                }) { Text("Report") }
            },
            dismissButton = { TextButton(onClick = { other = false }) { Text("Cancel") } },
        )
    }
}

/** Sync's "Tag user": a label of your own for [user], shown beside their name. Empty is refused, as in Sync. */
@Composable
fun TagUserDialog(user: String, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val current = remember(user) { container.tags.tagOf(user) }
    var text by remember(user) { mutableStateOf(current.orEmpty()) }
    var empty by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag u/$user") },
        text = {
            OutlinedTextField(
                text,
                {
                    text = it.take(40)
                    empty = false
                },
                label = { Text("Tag") },
                singleLine = true,
                isError = empty,
                supportingText = if (empty) ({ Text("Tag name must not be empty.") }) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    empty = true
                } else {
                    container.tags.set(user, text)
                    Toast.makeText(context, "Tag added: ${text.trim()}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = {
                    container.tags.set(user, null)
                    Toast.makeText(context, "Tag removed", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("Remove tag") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
