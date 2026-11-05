@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.home.HomeTab
import io.github.radixtrator.resync.ui.home.ShellScaffold
import io.github.radixtrator.resync.ui.home.quickActions
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.InboxBox
import io.github.radixtrator.resync.data.InboxItem
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(container: AppContainer) : ListingViewModel<InboxItem>(container) {
    private val _box = MutableStateFlow(InboxBox.INBOX)
    val box = _box.asStateFlow()

    init {
        if (container.username.value != null) reload()
    }

    override suspend fun fetch(after: String?): Listing<InboxItem> = container.api.inbox(_box.value, after)
    override fun keyOf(item: InboxItem) = item.fullname

    fun setBox(box: InboxBox) {
        if (box == _box.value) return
        _box.value = box
        updateItems { emptyList() }
        reload()
    }

    fun markRead(item: InboxItem) {
        if (!item.isNew) return
        updateItems { list -> list.map { if (it.fullname == item.fullname) it.copy(isNew = false) else it } }
        container.setInboxCount((container.inboxCount.value - 1).coerceAtLeast(0))
        viewModelScope.launch { runCatching { container.api.markRead(listOf(item.fullname)) } }
    }

    fun markAllRead() {
        updateItems { list -> list.map { it.copy(isNew = false) } }
        container.setInboxCount(0)
        viewModelScope.launch {
            runCatching { container.api.markAllRead() }.onFailure { showMessage("Couldn't mark all read: ${it.message}") }
        }
    }
}

@Composable
fun InboxScreen(route: InboxRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { InboxViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val box by vm.box.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val loggedIn = LocalAccount.current != null
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var boxOpen by rememberSaveable { mutableStateOf(false) }
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    MessageEffect(state.message, snackbar, vm::messageShown)

    SwipeBack(enabled = !route.home && LocalSettings.current.swipeBack, onBack = nav::pop) {
        ShellScaffold(
            container = container,
            home = route.home,
            tab = HomeTab.INBOX,
            smallTitle = "Inbox".takeIf { headerGone },
            scrollBehavior = scrollBehavior,
            snackbar = snackbar,
            quickActions = quickActions(container, onRefresh = vm::refresh),
            onReselectTab = { scope.launch { listState.animateScrollToItem(0) } },
            actions = {
                if (loggedIn) IconButton(onClick = vm::markAllRead) { Icon(AppIcons.DoubleCheck, "Mark all as read") }
            },
        ) { padding ->
            if (!loggedIn) {
                Column(
                    Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Log in to see your inbox")
                    Spacer(Modifier.padding(8.dp))
                    Button(onClick = { nav.push(LoginRoute) }) { Text("Log in") }
                }
                return@ShellScaffold
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            ) {
                PagedList(
                    state = state,
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    listState = listState,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    emptyText = "No messages",
                    header = {
                        item(key = "header") {
                            LargeHeader("Inbox") {
                                HeaderChip(box.label, Icons.Outlined.Email, { boxOpen = true }, dropdown = true)
                                HeaderChip("New message", Icons.Outlined.Edit, { nav.push(ComposeRoute(ComposeMode.MESSAGE)) })
                                HeaderChip("Mark all as read", AppIcons.DoubleCheck, vm::markAllRead)
                            }
                        }
                    },
                ) { item ->
                    InboxRow(
                        item = item,
                        sent = box == InboxBox.SENT,
                        onOpen = {
                            vm.markRead(item)
                            if (item.isComment && item.postId != null) {
                                nav.push(PostRoute(item.postId, item.commentId, context = 3))
                            }
                        },
                        onReply = {
                            vm.markRead(item)
                            nav.push(ComposeRoute(ComposeMode.REPLY, target = item.fullname, quote = item.body))
                        },
                    )
                }
            }
        }
    }

    if (boxOpen) {
        ModalBottomSheet(onDismissRequest = { boxOpen = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetTitle("Messages", Modifier.padding(top = 0.dp))
                InboxBox.entries.forEach { b ->
                    OptionRow(b.label, icon = null, selected = b == box, onClick = {
                        boxOpen = false
                        vm.setBox(b)
                    })
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, sent: Boolean, onOpen: () -> Unit, onReply: () -> Unit) {
    val nav = LocalNavigator.current
    // Private messages expand in place; comment replies open their thread.
    var expanded by rememberSaveable(item.fullname) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (item.isNew) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = {
                if (item.isComment) onOpen() else {
                    expanded = !expanded
                    onOpen()
                }
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isNew) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (item.isComment) "${item.subject.replaceFirstChar { it.uppercase() }} in r/${item.subreddit}" else item.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (item.isNew) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(Format.timeAgo(item.createdUtc), style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Text(
            if (sent) "to u/${item.dest}" else "from u/${item.author}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable { nav.push(UserRoute(if (sent) item.dest else item.author)) },
        )
        item.postTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(
            rememberRedditHtml(item.bodyHtml),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded || item.isComment) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!sent && (item.isComment || expanded)) {
            Row {
                TextButton(onClick = onReply) { Text("Reply") }
                if (item.isComment) TextButton(onClick = onOpen) { Text("Context") }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
