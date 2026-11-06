@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.LinkFlair
import io.github.radixtrator.resync.ui.feed.RulesSheet
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.PostRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubmitViewModel(private val container: AppContainer) : ViewModel() {
    data class State(val sending: Boolean = false, val postId: String? = null, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun submit(subreddit: String, title: String, isLink: Boolean, body: String, nsfw: Boolean, spoiler: Boolean, sendReplies: Boolean, flair: LinkFlair?) {
        if (_state.value.sending) return
        if (container.username.value == null) {
            _state.value = State(error = "Log in to post")
            return
        }
        _state.value = State(sending = true)
        viewModelScope.launch {
            runCatching {
                container.api.submit(subreddit.trim().removePrefix("r/").removePrefix("/r/"), title.trim(), isLink, body.trim(), nsfw, spoiler, sendReplies, flair)
            }.onSuccess { _state.value = State(postId = it) }
                .onFailure { e -> _state.value = State(error = e.message ?: "Couldn't post") }
        }
    }

    /** A photo post from the picked image at [uri]; finishes with its id, or goes back with a note if Reddit hasn't listed it yet. */
    fun submitPhoto(context: android.content.Context, subreddit: String, title: String, uri: android.net.Uri, nsfw: Boolean, spoiler: Boolean, sendReplies: Boolean, flair: LinkFlair?) {
        if (_state.value.sending) return
        val me = container.username.value
        if (me == null) {
            _state.value = State(error = "Log in to post")
            return
        }
        _state.value = State(sending = true)
        viewModelScope.launch {
            runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("Couldn't read that image")
                val name = "photo." + (mime.substringAfter('/', "jpg").substringBefore(';').let { if (it == "jpeg") "jpg" else it })
                container.api.submitImage(subreddit.trim().removePrefix("r/").removePrefix("/r/"), title.trim(), me, name, mime, bytes, nsfw, spoiler, sendReplies, flair)
            }.onSuccess { id -> _state.value = if (id != null) State(postId = id) else State(error = "Posted. Reddit is still processing the photo; check your profile") }
                .onFailure { e -> _state.value = State(error = e.message ?: "Couldn't post") }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}

/** Text and link posts. (Image posts need Reddit's media-upload flow; not supported yet.) */
@Composable
fun SubmitScreen(initialSubreddit: String?, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { SubmitViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var subreddit by rememberSaveable { mutableStateOf(initialSubreddit.orEmpty()) }
    var title by rememberSaveable { mutableStateOf("") }
    var isLink by rememberSaveable { mutableStateOf(false) }
    var isPhoto by rememberSaveable { mutableStateOf(false) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photo = uri.toString()
    }
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var url by rememberSaveable { mutableStateOf("") }
    var nsfw by rememberSaveable { mutableStateOf(false) }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    var sendReplies by rememberSaveable { mutableStateOf(true) }
    var flairText by rememberSaveable { mutableStateOf<String?>(null) }
    var flairId by rememberSaveable { mutableStateOf<String?>(null) }
    var flairColor by rememberSaveable { mutableStateOf<Int?>(null) }
    var flairOpen by rememberSaveable { mutableStateOf(false) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    val community = subreddit.trim().removePrefix("r/").removePrefix("/r/")
    val snackbar = remember { SnackbarHostState() }
    val canPost = subreddit.isNotBlank() && title.isNotBlank() && (isPhoto && photo != null || !isPhoto && (!isLink || url.startsWith("http"))) && !state.sending

    LaunchedEffect(state.postId) { state.postId?.let { nav.replaceTop(PostRoute(it)) } }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.errorShown()
        }
    }

    if (flairOpen) {
        FlairPickerSheet(container, community, onDismiss = { flairOpen = false }) { picked ->
            flairText = picked?.text
            flairId = picked?.id
            flairColor = picked?.color
            flairOpen = false
        }
    }
    if (rulesOpen) RulesSheet(container, community, onDismiss = { rulesOpen = false })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.Default.Close, "Discard") } },
                title = { Text("Create post") },
                actions = {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            enabled = canPost,
                            onClick = {
                                val flair = flairText?.let { LinkFlair(it, flairColor, flairId) }
                                if (isPhoto) {
                                    vm.submitPhoto(context, subreddit, title, android.net.Uri.parse(photo), nsfw, spoiler, sendReplies, flair)
                                } else {
                                    vm.submit(subreddit, title, isLink, if (isLink) url else body.text, nsfw, spoiler, sendReplies, flair)
                                }
                            },
                        ) { Text("Post") }
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
            OutlinedTextField(
                value = subreddit,
                onValueChange = {
                    // A flair belongs to its community.
                    if (it.trim().removePrefix("r/") != subreddit) {
                        flairText = null
                        flairId = null
                    }
                    subreddit = it.trim().removePrefix("r/")
                },
                label = { Text("Community") },
                prefix = { Text("r/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (community.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { flairOpen = true }, label = { Text(flairText?.let { "Flair: $it" } ?: "Add flair") })
                    AssistChip(onClick = { rulesOpen = true }, label = { Text("Community rules") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isLink && !isPhoto, onClick = { isLink = false; isPhoto = false }, label = { Text("Text") })
                FilterChip(selected = isLink && !isPhoto, onClick = { isLink = true; isPhoto = false }, label = { Text("Link") })
                FilterChip(selected = isPhoto, onClick = { isPhoto = true }, label = { Text("Photo") })
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(300) },
                label = { Text("Title") },
                supportingText = { Text("${title.length}/300") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isPhoto) {
                TextButton(onClick = { picker.launch("image/*") }) { Text(if (photo == null) "Choose a photo" else "Photo chosen: change") }
                photo?.let {
                    coil3.compose.AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
                }
            } else if (isLink) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MarkdownToolbar(body, { body = it })
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Body (optional, markdown)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = nsfw, onCheckedChange = { nsfw = it })
                Text("NSFW")
                Checkbox(checked = spoiler, onCheckedChange = { spoiler = it })
                Text("Spoiler")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sendReplies, onCheckedChange = { sendReplies = it })
                Text("Send replies to my inbox")
            }
        }
    }
}

/** Flairs the community lets you pick on a new post (only those with a template id can be submitted). */
@Composable
private fun FlairPickerSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit, onPick: (LinkFlair?) -> Unit) {
    val flairs by androidx.compose.runtime.produceState<Result<List<LinkFlair>>?>(null, subreddit) {
        value = runCatching { container.api.linkFlairs(subreddit).filter { it.id != null } }
            .onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Flair", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            when (val result = flairs) {
                null -> CircularProgressIndicator(Modifier.padding(16.dp))
                else -> result.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) Text("r/$subreddit has no flairs you can pick")
                        TextButton(onClick = { onPick(null) }) { Text("No flair") }
                        list.forEach { f -> AssistChip(onClick = { onPick(f) }, label = { Text(f.text) }) }
                    },
                    onFailure = { Text("Couldn't load flairs: ${it.message}") },
                )
            }
        }
    }
}
