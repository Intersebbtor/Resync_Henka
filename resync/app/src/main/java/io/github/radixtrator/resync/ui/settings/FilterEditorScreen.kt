@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.navigation.LocalNavigator

/**
 * Sync's FilterActivity for one [type] (Settings › Filters › Keyword filters, …): a large title, an add
 * field with an Add button, the list with a remove button per entry, and "Remove all". Entries are stored
 * normalized ([FilterType.normalize]), so "r/Pics" and "pics" are the same filter.
 */
@Composable
fun FilterEditorScreen(type: FilterType, container: AppContainer) {
    val nav = LocalNavigator.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val filters by container.filters.filters.collectAsStateWithLifecycle()
    val entries = type.of(filters).sorted()
    var text by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val add = {
        container.filters.set(type, text)
        text = ""
    }

    SwipeBack(enabled = settings.swipeBack, onBack = nav::pop) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {},
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding()) {
                item(key = "header") {
                    LargeHeader(title = type.title) {
                        if (entries.isNotEmpty()) HeaderChip(label = "Remove all", icon = Icons.Outlined.Delete, onClick = { confirmClear = true })
                    }
                }
                item(key = "add") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            label = { Text(type.hint) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (type.normalize(text).isNotEmpty()) add() }),
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = add, enabled = type.normalize(text).isNotEmpty()) { Text("Add") }
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "${type.empty}\n${type.summary}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(entries, key = { it }) { entry ->
                    val label = type.describe(entry)
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            IconButton(onClick = { container.filters.remove(type, entry) }) { Icon(Icons.Default.Close, "Stop filtering $label") }
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all?") },
            text = { Text("Every entry in ${type.title.lowercase()} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    container.filters.clear(type)
                    confirmClear = false
                }) { Text("Remove all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
