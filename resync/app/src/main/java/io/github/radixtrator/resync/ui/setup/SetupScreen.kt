@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.data.ApiConfig

@Composable
fun SetupScreen(
    container: AppContainer,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val existing = remember { container.configStore.load() }
    var clientId by rememberSaveable { mutableStateOf(existing?.clientId.orEmpty()) }
    var redirectUri by rememberSaveable { mutableStateOf(existing?.redirectUri ?: ApiConfig.DEFAULT_REDIRECT_URI) }
    var userAgent by rememberSaveable { mutableStateOf(existing?.userAgent ?: ApiConfig.DEFAULT_USER_AGENT) }
    val parsedId = ApiConfig.parseClientId(clientId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reddit API") },
                navigationIcon = {
                    if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to Reddit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Reddit only lets registered apps use its API, and it no longer hands out new registrations. " +
                    "Resync uses a client ID you supply. It's stored only on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where to get one", fontWeight = FontWeight.SemiBold)
                    Text("• Your own Reddit \"installed app\", if you made one before the shutdown. Use its redirect URI too.")
                    Text(
                        "• Otherwise, the approach Boost for Reddit documents: log in to RedReader once, and Reddit " +
                            "emails you \"App ID: …\". Paste that whole line below and keep the default redirect URI " +
                            "and user agent.",
                    )
                }
            }
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client ID") },
                singleLine = true,
                isError = clientId.isNotBlank() && parsedId == null,
                supportingText = {
                    when {
                        clientId.isNotBlank() && parsedId == null -> Text("That doesn't look like a client ID")
                        parsedId != null && parsedId != clientId.trim() -> Text("Will use: $parsedId")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = redirectUri,
                onValueChange = { redirectUri = it },
                label = { Text("Redirect URI") },
                singleLine = true,
                supportingText = { Text("Must match the app the client ID belongs to exactly") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = userAgent,
                onValueChange = { userAgent = it },
                label = { Text("User agent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = parsedId != null && redirectUri.isNotBlank() && userAgent.isNotBlank(),
                onClick = {
                    container.saveConfig(ApiConfig(parsedId!!, redirectUri.trim(), userAgent.trim()))
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = {
                        container.enableDemo()
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Debug: browse bundled demo data instead") }
            }
        }
    }
}
