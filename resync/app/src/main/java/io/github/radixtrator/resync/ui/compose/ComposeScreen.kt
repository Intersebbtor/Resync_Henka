@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.ContentEvent
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ComposeViewModel(private val container: AppContainer, private val route: ComposeRoute) : ViewModel() {
    data class State(val sending: Boolean = false, val done: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun send(text: String, to: String, subject: String) {
        if (_state.value.sending) return
        _state.value = State(sending = true)
        viewModelScope.launch {
            runCatching {
                when (route.mode) {
                    ComposeMode.REPLY -> {
                        val target = route.target!!
                        container.api.reply(target, text)?.let { container.emit(ContentEvent.Replied(target, it)) }
                    }
                    ComposeMode.EDIT -> {
                        val target = route.target!!
                        val updated = container.api.edit(target, text)
                        container.emit(ContentEvent.Edited(target, text, updated?.bodyHtml))
                    }
                    ComposeMode.MESSAGE -> container.api.sendMessage(to.trim().removePrefix("u/").removePrefix("/u/"), subject.trim(), text)
                }
            }.onSuccess { _state.value = State(done = true) }
                .onFailure { e -> _state.value = State(error = e.message ?: "Couldn't send") }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}

/** Markdown editor for replies, edits and private messages. */
@Composable
fun ComposeScreen(route: ComposeRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { ComposeViewModel(container, route) }
    val state by vm.state.collectAsStateWithLifecycle()
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(route.initial, TextRange(route.initial.length)))
    }
    var to by rememberSaveable { mutableStateOf(route.to) }
    var subject by rememberSaveable { mutableStateOf(route.subject) }
    val snackbar = remember { SnackbarHostState() }
    val bodyFocus = remember { FocusRequester() }
    val isMessage = route.mode == ComposeMode.MESSAGE
    val canSend = body.text.isNotBlank() && !state.sending && (!isMessage || (to.isNotBlank() && subject.isNotBlank()))

    LaunchedEffect(state.done) { if (state.done) nav.pop() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.errorShown()
        }
    }
    LaunchedEffect(Unit) { if (!isMessage || route.to.isNotBlank()) bodyFocus.requestFocus() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.Default.Close, "Discard") } },
                title = {
                    Text(
                        when (route.mode) {
                            ComposeMode.REPLY -> "Reply"
                            ComposeMode.EDIT -> "Edit"
                            ComposeMode.MESSAGE -> "New message"
                        },
                    )
                },
                actions = {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.send(body.text, to, subject) }, enabled = canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
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
            route.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        quote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (isMessage) {
                OutlinedTextField(to, { to = it }, label = { Text("To") }, prefix = { Text("u/") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            MarkdownToolbar(body, { body = it })
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Write something (markdown)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).focusRequester(bodyFocus),
            )
        }
    }
}
