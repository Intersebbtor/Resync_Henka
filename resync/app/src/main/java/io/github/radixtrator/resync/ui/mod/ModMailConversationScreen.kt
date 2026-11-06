@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.mod

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.ModMailMessage
import io.github.radixtrator.resync.data.ModMailThread
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModMailConversationState(
    val thread: ModMailThread? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val message: String? = null,
)

class ModMailConversationViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(ModMailConversationState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val thread = container.api.modMailThread(id)
                _state.update { it.copy(thread = thread, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this conversation") }
            }
        }
    }

    fun setDraft(text: String) = _state.update { it.copy(draft = text) }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val thread = container.api.replyModMail(id, text)
                // Some answers leave out older messages; keep what we had if the reply came back thinner.
                val kept = _state.value.thread?.messages.orEmpty()
                val merged = if (thread.messages.size >= kept.size) thread else thread.copy(messages = (kept + thread.messages).distinctBy { it.id })
                _state.update { it.copy(thread = merged, draft = "", sending = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, message = e.message ?: "Couldn't send the reply") }
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val thread = _state.value.thread ?: return
        viewModelScope.launch {
            runCatching { container.api.setModMailArchived(id, archived) }
                .onSuccess {
                    _state.update { s -> s.copy(thread = thread.copy(conversation = thread.conversation.copy(archived = archived)), message = if (archived) "Archived" else "Unarchived") }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't do that") } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

/** One mod mail conversation: its messages, oldest first, and a reply field at the bottom. */
@Composable
fun ModMailConversationScreen(id: String, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel(key = "modmail-conversation:$id") { ModMailConversationViewModel(container, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val conversation = state.thread?.conversation
    val messages = state.thread?.messages.orEmpty()
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    // Open at, and follow, the newest message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text(
                                conversation?.subject?.ifBlank { null } ?: "Mod mail",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            conversation?.community?.takeIf { it.isNotEmpty() }?.let {
                                Text("r/$it", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    actions = {
                        if (conversation != null) {
                            TextButton(onClick = { vm.setArchived(!conversation.archived) }) {
                                Text(if (conversation.archived) "Unarchive" else "Archive")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.thread == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.thread == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "Couldn't load this conversation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = vm::load) { Text("Retry") }
                        }
                        else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            items(messages, key = { it.id }) { message ->
                                ModMailMessageItem(message, onAuthor = { nav.push(UserRoute(message.author)) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                if (state.thread != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = vm::setDraft,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Reply") },
                            enabled = !state.sending,
                            maxLines = 6,
                        )
                        if (state.sending) {
                            Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        } else {
                            IconButton(onClick = vm::send, enabled = state.draft.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModMailMessageItem(message: ModMailMessage, onAuthor: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAuthor, contentPadding = PaddingValues(0.dp)) {
                Text(
                    "u/${message.author}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (message.authorIsMod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            val tags = buildList {
                if (message.authorIsMod) add("mod")
                if (message.authorHidden) add("as subreddit")
                if (message.internal) add("private note")
                if (message.dateUtc > 0) add(Format.timeAgo(message.dateUtc))
            }
            if (tags.isNotEmpty()) {
                Text(" • " + tags.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        if (message.bodyHtml.isNotBlank()) {
            RedditBodyText(rememberRedditHtml(message.bodyHtml), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(message.bodyMarkdown, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
