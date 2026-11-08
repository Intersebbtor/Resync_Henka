@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.mod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.ModMailConversation
import io.github.radixtrator.resync.data.ModMailState
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.ModMailConversationRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** New-style mod mail: one [ModMailState] box at a time, narrowed to [subreddit] unless [allCommunities] is on. */
class ModMailViewModel(container: AppContainer, private val subreddit: String?) : ListingViewModel<ModMailConversation>(container) {
    private val _box = MutableStateFlow(ModMailState.ALL)
    val box = _box.asStateFlow()
    private val _allCommunities = MutableStateFlow(subreddit == null)
    val allCommunities = _allCommunities.asStateFlow()

    init {
        reload()
    }

    override suspend fun fetch(after: String?): Listing<ModMailConversation> {
        val communities = if (_allCommunities.value || subreddit == null) emptyList() else listOf(subreddit)
        return container.api.modMail(_box.value, communities, after)
    }

    override fun keyOf(item: ModMailConversation) = item.id

    fun setBox(box: ModMailState) {
        if (box == _box.value) return
        _box.value = box
        updateItems { emptyList() }
        reload()
    }

    fun setAllCommunities(all: Boolean) {
        if (all == _allCommunities.value) return
        _allCommunities.value = all
        updateItems { emptyList() }
        reload()
    }

    /** Archives or unarchives [conversation]; it leaves boxes it no longer belongs in. */
    fun setArchived(conversation: ModMailConversation, archived: Boolean) {
        viewModelScope.launch {
            runCatching { container.api.setModMailArchived(conversation.id, archived) }
                .onSuccess {
                    val stays = _box.value == ModMailState.ALL || (_box.value == ModMailState.ARCHIVED) == archived
                    updateItems { list ->
                        if (stays) list.map { if (it.id == conversation.id) it.copy(archived = archived) else it }
                        else list.filterNot { it.id == conversation.id }
                    }
                    showMessage(if (archived) "Archived" else "Unarchived")
                }
                .onFailure { e -> showMessage(e.message ?: "Couldn't do that") }
        }
    }
}

/** The mod mail list: a box chip (All, New, In progress, Archived) over the conversations, newest first. */
@Composable
fun ModMailScreen(subreddit: String?, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel(key = "modmail:${subreddit.orEmpty()}") { ModMailViewModel(container, subreddit) }
    val state by vm.state.collectAsStateWithLifecycle()
    val box by vm.box.collectAsStateWithLifecycle()
    val allCommunities by vm.allCommunities.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var boxMenu by remember { mutableStateOf(false) }
    var communityMenu by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text("Mod mail", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (allCommunities || subreddit == null) "All communities" else "r/$subreddit",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        if (subreddit != null) {
                            TextButton(onClick = { communityMenu = true }) { Text(if (allCommunities) "All ▾" else "r/$subreddit ▾", maxLines = 1) }
                            DropdownMenu(expanded = communityMenu, onDismissRequest = { communityMenu = false }) {
                                DropdownMenuItem(text = { Text("r/$subreddit") }, onClick = { communityMenu = false; vm.setAllCommunities(false) })
                                DropdownMenuItem(text = { Text("All communities") }, onClick = { communityMenu = false; vm.setAllCommunities(true) })
                            }
                        }
                        TextButton(onClick = { boxMenu = true }) { Text("${box.label} ▾") }
                        DropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                            ModMailState.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) }, onClick = { boxMenu = false; vm.setBox(s) })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                PagedList(
                    state = state,
                    key = { it.id },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    emptyText = "No mod mail in ${box.label.lowercase()}",
                ) { conversation ->
                    Column {
                        ModMailRow(
                            conversation,
                            onOpen = { nav.push(ModMailConversationRoute(conversation.id)) },
                            onArchive = { vm.setArchived(conversation, !conversation.archived) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** Subject (bold while unread), then community, participants and age; the button archives or unarchives. */
@Composable
private fun ModMailRow(conversation: ModMailConversation, onOpen: () -> Unit, onArchive: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                conversation.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal,
                color = if (conversation.highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (conversation.community.isNotEmpty()) add("r/${conversation.community}")
                if (conversation.lastUpdatedUtc > 0) add(Format.timeAgo(conversation.lastUpdatedUtc))
                if (conversation.numMessages > 0) add(if (conversation.numMessages == 1) "1 message" else "${conversation.numMessages} messages")
                if (conversation.archived) add("archived")
            }.joinToString(" • ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (conversation.participants.isNotEmpty()) {
                Text(
                    conversation.participants.joinToString(", ") { "u/$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onArchive) { Text(if (conversation.archived) "Unarchive" else "Archive") }
    }
}
