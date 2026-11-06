@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.ModSection
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModViewModel(container: AppContainer, private val subreddit: String) : ListingViewModel<ProfileItem>(container) {
    private val _section = MutableStateFlow(ModSection.MODQUEUE)
    val section = _section.asStateFlow()

    init {
        reload()
    }

    override suspend fun fetch(after: String?): Listing<ProfileItem> = container.api.modListing(subreddit, _section.value, after)
    override fun keyOf(item: ProfileItem) = item.fullname
    override fun postOf(item: ProfileItem): Post? = (item as? ProfileItem.PostItem)?.post
    override fun withPost(item: ProfileItem, post: Post): ProfileItem = ProfileItem.PostItem(post)

    fun setSection(section: ModSection) {
        _section.value = section
        updateItems { emptyList() }
        reload()
    }

    /** Approves or removes [item]; it leaves the list once Reddit accepts. */
    fun moderate(item: ProfileItem, approve: Boolean, spam: Boolean = false) {
        viewModelScope.launch {
            runCatching { if (approve) container.api.approve(item.fullname) else container.api.remove(item.fullname, spam) }
                .onSuccess { updateItems { list -> list.filterNot { it.fullname == item.fullname } } }
                .onFailure { e -> _state.value = _state.value.copy(message = e.message ?: "Couldn't do that") }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }
}

/** Sync's Moderator screen: a section chip over the community's queue, each item with Approve / Remove / Spam. */
@Composable
fun ModScreen(subreddit: String, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel(key = "mod:$subreddit") { ModViewModel(container, subreddit) }
    val state by vm.state.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val callbacks = remember(vm) { vm.postCallbacks(allowHide = false) }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
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
                            Text("Moderator", style = MaterialTheme.typography.titleMedium)
                            Text("r/$subreddit", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TextButton(onClick = { menuOpen = true }) { Text("${section.label} ▾") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ModSection.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) }, onClick = { menuOpen = false; vm.setSection(s) })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                PagedList(
                    state = state,
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    emptyText = "Nothing in ${section.label.lowercase()}",
                ) { item ->
                    Column {
                        when (item) {
                            is ProfileItem.PostItem -> PostListItem(item.post, showSubreddit = false, callbacks = callbacks)
                            is ProfileItem.CommentItem -> CommentCard(item.comment) {
                                nav.push(PostRoute(item.comment.postId, item.comment.id, context = 3))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.moderate(item, approve = true) }) { Text("Approve") }
                            TextButton(onClick = { vm.moderate(item, approve = false) }) { Text("Remove") }
                            TextButton(onClick = { vm.moderate(item, approve = false, spam = true) }) { Text("Spam") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
