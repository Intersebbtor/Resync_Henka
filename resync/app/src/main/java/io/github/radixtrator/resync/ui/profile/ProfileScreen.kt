@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.UserComment
import io.github.radixtrator.resync.data.UserProfile
import io.github.radixtrator.resync.data.UserSort
import io.github.radixtrator.resync.data.UserWhere
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(container: AppContainer, val name: String, initialTab: UserWhere) :
    ListingViewModel<ProfileItem>(container) {

    private val _tab = MutableStateFlow(initialTab)
    val tab = _tab.asStateFlow()
    private val _sort = MutableStateFlow(UserSort.NEW)
    val sort = _sort.asStateFlow()
    private val _user = MutableStateFlow<UserProfile?>(null)
    val user = _user.asStateFlow()

    init {
        reload()
        viewModelScope.launch { runCatching { container.api.user(name) }.onSuccess { _user.value = it } }
    }

    override suspend fun fetch(after: String?): Listing<ProfileItem> =
        container.api.userContent(name, _tab.value, _sort.value, after)

    override fun keyOf(item: ProfileItem) = item.fullname
    override fun postOf(item: ProfileItem) = (item as? ProfileItem.PostItem)?.post
    override fun withPost(item: ProfileItem, post: Post) = ProfileItem.PostItem(post)

    fun setTab(tab: UserWhere) {
        if (tab == _tab.value) return
        _tab.value = tab
        updateItems { emptyList() }
        reload()
    }

    fun setSort(sort: UserSort) {
        if (sort == _sort.value) return
        _sort.value = sort
        updateItems { emptyList() }
        reload()
    }
}

@Composable
fun ProfileScreen(name: String, initialTab: UserWhere, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { ProfileViewModel(container, name, initialTab) }
    val state by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val isSelf = LocalAccount.current.equals(name, ignoreCase = true)
    val tabs = UserWhere.entries.filter { isSelf || !it.ownOnly }
    val callbacks = remember(vm) { vm.postCallbacks(allowHide = false) }
    val items = state.items.visiblePosts { (it as? ProfileItem.PostItem)?.post }

    MessageEffect(state.message, snackbar, vm::messageShown)

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = { Text("u/$name", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (!isSelf && LocalAccount.current != null) {
                            IconButton(onClick = { nav.push(ComposeRoute(ComposeMode.MESSAGE, to = name)) }) {
                                Icon(Icons.Default.Email, "Message")
                            }
                        }
                        UserSortMenu(vm::setSort)
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
                PagedList(
                    state = state.copy(items = items),
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    emptyText = "Nothing to show",
                    header = {
                        item(key = "header") { ProfileHeader(name, user) }
                        item(key = "tabs") {
                            SecondaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), edgePadding = 8.dp) {
                                tabs.forEach { t -> Tab(selected = t == tab, onClick = { vm.setTab(t) }, text = { Text(t.label) }) }
                            }
                        }
                    },
                ) { item ->
                    when (item) {
                        is ProfileItem.PostItem -> PostListItem(item.post, showSubreddit = true, callbacks = callbacks)
                        is ProfileItem.CommentItem -> ProfileCommentRow(item.comment) {
                            nav.push(PostRoute(item.comment.postId, item.comment.id, context = 3))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(name: String, user: UserProfile?) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
            user?.iconUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("u/$name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (user != null) {
                Text(
                    "${Format.count(user.linkKarma)} post karma · ${Format.count(user.commentKarma)} comment karma",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Redditor for ${Format.timeAgo(user.createdUtc)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                user.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A comment shown outside its thread, with the post it belongs to. */
@Composable
fun ProfileCommentRow(comment: UserComment, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column(Modifier.combinedClickable(onClick = onOpen).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Comment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "r/${comment.subreddit} · ${comment.postTitle}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RedditBodyText(rememberRedditHtml(comment.bodyHtml), MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
            Text(
                "${Format.count(comment.score)} points · ${Format.timeAgo(comment.createdUtc)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserSortMenu(onPick: (UserSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UserSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label) }, onClick = { open = false; onPick(sort) }) }
        }
    }
}
