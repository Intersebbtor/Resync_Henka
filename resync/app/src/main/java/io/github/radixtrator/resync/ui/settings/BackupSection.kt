package io.github.radixtrator.resync.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Backup
import io.github.radixtrator.resync.data.BackupDocument
import io.github.radixtrator.resync.data.BackupFormatException
import io.github.radixtrator.resync.data.BackupPart
import io.github.radixtrator.resync.data.BackupPasswordException
import io.github.radixtrator.resync.data.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shortest password Backup now accepts for the encrypted accounts. */
private const val MIN_PASSWORD = 8

/**
 * Sync's Backup screen: Backup now and Restore from backup. The system file picker stands in for Sync's
 * "Change backup directory".
 *
 * Backup now can take the signed-in accounts along, encrypted with a password. A restore asks first: which parts to
 * replace, whether to take the Reddit API setup, and (with the password) the accounts. It saves the device's data
 * as it was before, so "Undo last restore" can put it back, then restarts the app, since the stores keep what they
 * loaded in memory.
 */
@Composable
internal fun BackupSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportOptions by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) } // set while the file picker is open
    var toRestore by remember { mutableStateOf<BackupDocument?>(null) }
    var restored by remember { mutableStateOf<RestoreResult?>(null) }
    var undone by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    val snapshotCreated = remember(restored, undone) { container.preRestoreSnapshotCreated() }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri == null) {
            password?.fill(' ')
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                // Deriving the accounts' key is deliberately slow, so it stays off the main thread.
                val text = withContext(Dispatchers.Default) { container.exportBackup(password) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("couldn't open the file")
                }
                // Read it back, so a backup that can't be restored is caught now rather than when it's needed.
                withContext(Dispatchers.IO) {
                    val written = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    check(written == text) { "the saved file doesn't match" }
                    Backup.decode(written)
                }
            }
            password?.fill(' ')
            val message = result.fold(
                { if (it.accounts != null) "Backup saved, with ${plural(it.accountNames.size, "account")}" else "Backup saved" },
                { "Couldn't save the backup: ${it.message}" },
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("couldn't open the file")
                Backup.decode(text)
            }
                .onSuccess { toRestore = it }
                .onFailure {
                    val message = (it as? BackupFormatException)?.message ?: "Couldn't read the backup: ${it.message}"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    Section("Backup")
    Text(
        "Backups hold this device's settings, filters, tags, favorites, watched items, drafts and Reddit API setup. " +
            "Signed-in accounts can be included too, protected by a password.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    ListItem(
        headlineContent = { Text("Backup now") },
        supportingContent = { Text("Save a backup file wherever you choose") },
        modifier = Modifier.clickable {
            if (container.hasBackupAccounts) exportOptions = true else backupLauncher.launch(Backup.fileName())
        },
    )
    ListItem(
        headlineContent = { Text("Restore from backup") },
        supportingContent = { Text("Replace this device's data with a backup file") },
        modifier = Modifier.clickable { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
    )
    if (snapshotCreated != null) {
        ListItem(
            headlineContent = { Text("Undo last restore") },
            supportingContent = {
                Text("Put back everything as it was before restoring" + snapshotCreated.takeIf { it.length >= 10 }?.let { " on ${it.take(10)}" }.orEmpty())
            },
            modifier = Modifier.clickable { confirmUndo = true },
        )
    }

    if (exportOptions) {
        ExportDialog(
            onDismiss = { exportOptions = false },
            onExport = { password ->
                exportOptions = false
                exportPassword = password
                backupLauncher.launch(Backup.fileName())
            },
        )
    }

    toRestore?.let { doc ->
        RestoreDialog(
            doc = doc,
            onDismiss = { toRestore = null },
            onRestore = { parts, includeApi, password, showError ->
                scope.launch {
                    val result = runCatching {
                        val accounts = password?.let { withContext(Dispatchers.Default) { Backup.decryptAccounts(doc, it) } }
                        password?.fill(' ')
                        withContext(Dispatchers.IO) { container.restoreBackup(doc, includeApi, parts, accounts) }
                    }
                    result
                        .onSuccess {
                            toRestore = null
                            restored = it
                        }
                        .onFailure {
                            if (it is BackupPasswordException) showError(it.message ?: "Wrong password")
                            else {
                                toRestore = null
                                Toast.makeText(context, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            },
        )
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Undo last restore?") },
            text = { Text("Everything, accounts included, goes back to how it was just before the last restore. Resync restarts afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { container.undoRestore() } }
                            .onSuccess { undone = true }
                            .onFailure { Toast.makeText(context, "Couldn't undo: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        )
    }

    // No way out but the restart: anything the in-memory stores saved now would overwrite the restored data.
    val restartMessage = when {
        undone -> "Your data is back as it was before the restore."
        else -> restored?.let { r ->
            listOfNotNull(
                "The backup was restored.",
                "The restored API setup uses a different client ID, so the accounts that were here were signed out.".takeIf { r.signedOut },
                "Signed in to ${plural(r.accountsRestored, "account")} from the backup.".takeIf { r.accountsRestored > 0 },
                "You can undo this from Settings › Backup.",
            ).joinToString(" ")
        }
    }
    restartMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (undone) "Restore undone" else "Backup restored") },
            text = { Text("$message Resync restarts to load it.") },
            confirmButton = { TextButton(onClick = { restartApp(context) }) { Text("Restart") } },
        )
    }
}

/** Backup now's options when there are accounts: leave them out, or include them under a password. */
@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (password: CharArray?) -> Unit) {
    var includeAccounts by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val problem = when {
        !includeAccounts -> null
        password.length < MIN_PASSWORD -> "At least $MIN_PASSWORD characters"
        password != confirm -> "The passwords don't match"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup now") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CheckRow(
                    checked = includeAccounts,
                    onChange = { includeAccounts = it },
                    title = "Include signed-in accounts",
                    subtitle = "Restoring signs you straight back in. Their sign-ins are encrypted with a password you'll need to restore them; it can't be recovered if forgotten.",
                )
                if (includeAccounts) {
                    PasswordField(password, { password = it }, "Password", Modifier.padding(top = 8.dp))
                    PasswordField(confirm, { confirm = it }, "Confirm password", Modifier.padding(top = 4.dp), error = problem.takeIf { confirm.isNotEmpty() || password.length >= MIN_PASSWORD })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = problem == null,
                onClick = { onExport(if (includeAccounts) password.toCharArray() else null) },
            ) { Text("Choose location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Restore from backup's question: which parts to replace, whether to take the API setup, and the accounts (with
 * their password). [onRestore] gets the choices and a way to show a wrong-password error in the dialog.
 */
@Composable
private fun RestoreDialog(
    doc: BackupDocument,
    onDismiss: () -> Unit,
    onRestore: (parts: Set<BackupPart>, includeApi: Boolean, password: CharArray?, showError: (String) -> Unit) -> Unit,
) {
    var parts by remember(doc) { mutableStateOf(BackupPart.entries.toSet()) }
    var includeApi by remember(doc) { mutableStateOf(false) }
    var includeAccounts by remember(doc) { mutableStateOf(false) }
    var password by remember(doc) { mutableStateOf("") }
    var error by remember(doc) { mutableStateOf<String?>(null) }
    var working by remember(doc) { mutableStateOf(false) }
    val names = doc.accountNames

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Restore from backup?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                doc.created?.let { created ->
                    Text("Backup from ${created.take(10)}", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Replace on this device:", Modifier.padding(top = 8.dp))
                BackupPart.entries.forEach { part ->
                    CheckRow(part in parts, { on -> parts = if (on) parts + part else parts - part }, part.label, dense = true)
                }
                if (doc.hasApiConfig) {
                    CheckRow(
                        checked = includeApi || includeAccounts,
                        onChange = { includeApi = it },
                        enabled = !includeAccounts,
                        title = "Reddit API setup",
                        subtitle = if (includeAccounts) "Needed for the backed-up accounts' sign-ins"
                        else "Client ID, redirect URI and user agent. A different client ID signs out the accounts on this device.",
                    )
                }
                if (doc.accounts != null) {
                    CheckRow(
                        checked = includeAccounts,
                        onChange = { includeAccounts = it; error = null },
                        title = "Signed-in accounts",
                        subtitle = names.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Signs in as ") { "u/$it" }
                            ?: "Signs in to the backed-up accounts",
                    )
                    if (includeAccounts) {
                        PasswordField(password, { password = it; error = null }, "Backup password", Modifier.padding(top = 4.dp), error = error)
                    }
                }
                Text(
                    (if (includeAccounts) "" else "Accounts on this device stay signed in. ") +
                        "What's here now is saved first, so you can undo the restore. Resync restarts to load the backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            if (working) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                TextButton(
                    enabled = (parts.isNotEmpty() || includeApi || includeAccounts) && (!includeAccounts || password.isNotEmpty()),
                    onClick = {
                        working = true
                        onRestore(parts, includeApi || includeAccounts, password.takeIf { includeAccounts }?.toCharArray()) {
                            working = false
                            error = it
                        }
                    },
                ) { Text("Restore") }
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    dense: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (dense) 0.dp else 8.dp).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun plural(count: Int, noun: String) = "$count $noun" + if (count == 1) "" else "s"
