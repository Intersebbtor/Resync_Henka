@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Multi
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** A name prompt for a custom feed: "New custom feed" and "Rename". [onConfirm] gets the trimmed name. */
@Composable
fun MultiNameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks before deleting [multi] on Reddit. */
@Composable
fun DeleteMultiDialog(multi: Multi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${multi.name}?") },
        text = { Text("The custom feed is deleted from your Reddit account. Its communities stay joined.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Manage communities" for a custom feed: its communities with a remove button each, and a field to add
 * one. Each change is sent to Reddit at once; [onChanged] runs after every successful change so the caller
 * can reload its list.
 */
@Composable
fun ManageMultiSheet(container: AppContainer, multi: Multi, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var subreddits by remember(multi.path) { mutableStateOf(multi.subreddits.sortedBy { it.lowercase() }) }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val typed = text.trim().removePrefix("/").removePrefix("r/").trim('/')

    fun change(subreddit: String, add: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                if (add) container.api.addToMulti(multi.path, subreddit) else container.api.removeFromMulti(multi.path, subreddit)
            }.onFailure { if (it is CancellationException) throw it }.fold(
                onSuccess = {
                    subreddits = if (add) {
                        (subreddits.filterNot { it.equals(subreddit, ignoreCase = true) } + subreddit).sortedBy { it.lowercase() }
                    } else {
                        subreddits.filterNot { it.equals(subreddit, ignoreCase = true) }
                    }
                    if (add) text = ""
                    onChanged()
                },
                onFailure = {
                    val what = if (add) "add r/$subreddit" else "remove r/$subreddit"
                    Toast.makeText(context, "Couldn't $what: ${it.message}", Toast.LENGTH_SHORT).show()
                },
            )
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            SheetTitle(multi.name, topPadding = 0.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Add a community") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (typed.isNotEmpty()) change(typed, add = true) }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { change(typed, add = true) }, enabled = typed.isNotEmpty() && !busy) { Text("Add") }
            }
            if (subreddits.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No communities yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(subreddits, key = { it.lowercase() }) { name ->
                        ListItem(
                            headlineContent = { Text("r/$name") },
                            trailingContent = {
                                IconButton(onClick = { change(name, add = false) }, enabled = !busy) { Icon(Icons.Default.Close, "Remove r/$name") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
