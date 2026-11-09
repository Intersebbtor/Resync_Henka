@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import io.github.radixtrator.resync.ui.common.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.RedditException
import io.github.radixtrator.resync.data.Trophy
import io.github.radixtrator.resync.data.UserProfile
import io.github.radixtrator.resync.data.UserSort
import io.github.radixtrator.resync.data.UserWhere
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.ChipMenu
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedGrid
import io.github.radixtrator.resync.ui.common.rememberPostPreloads
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.TagUserDialog
import io.github.radixtrator.resync.ui.common.columns
import io.github.radixtrator.resync.ui.common.gutter
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.spacing
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.feed.AboutHeader
import io.github.radixtrator.resync.ui.feed.AboutStat
import io.github.radixtrator.resync.ui.feed.CommentCard
import io.github.radixtrator.resync.ui.home.HomeTab
import io.github.radixtrator.resync.ui.home.ShellScaffold
import io.github.radixtrator.resync.ui.home.quickActions
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
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

    fun block() {
        viewModelScope.launch {
            runCatching { container.api.blockUser(name) }
                .onSuccess { showMessage("Blocked u/$name") }
                .onFailure {
                    // Logins from before Block existed lack Reddit's "account" permission.
                    val relogin = (it as? RedditException)?.code == 403
                    showMessage(if (relogin) "Log in again to let Resync block users" else "Couldn't block u/$name: ${it.message}")
                }
        }
    }
}

/**
 * Sync's profile: the avatar, karma and big username over chips (section ▾, sort ▾, About, Message,
 * Trophies, Tag user, Block, Share), then posts and comment cards in the feed's grid.
 */
@Composable
fun ProfileScreen(name: String, initialTab: UserWhere, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { ProfileViewModel(container, name, initialTab) }
    val state by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyStaggeredGridState(vm.scroll)
    val scope = rememberCoroutineScope()
    val me = LocalAccount.current
    val isSelf = me.equals(name, ignoreCase = true)
    val canAct = me != null && !isSelf
    val layout = LocalSettings.current.layout
    val columns = layout.columns(LocalConfiguration.current.screenWidthDp, LocalSettings.current.viewOptionsFor(layout).columns)
    val postPreloads = rememberPostPreloads(layout, columns)
    val callbacks = remember(vm) { vm.postCallbacks(allowHide = false) }
    val items = state.items.visiblePosts { (it as? ProfileItem.PostItem)?.post }
    // The toolbar shows the name only once the big header has scrolled away.
    val headerGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var trophiesOpen by rememberSaveable { mutableStateOf(false) }
    var tagging by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    val message = { nav.push(ComposeRoute(ComposeMode.MESSAGE, to = name)) }

    MessageEffect(state.message, snackbar, vm::messageShown)

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        ShellScaffold(
            container = container,
            home = false,
            tab = HomeTab.POSTS,
            smallTitle = name.takeIf { headerGone },
            scrollBehavior = scrollBehavior,
            snackbar = snackbar,
            quickActions = quickActions(container, onRefresh = vm::refresh),
            onReselectTab = { scope.launch { gridState.animateScrollToItem(0) } },
            // Sync has the sort in the toolbar as well as on its chip.
            actions = { SortMenu(vm::setSort) },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            ) {
                PagedGrid(
                    state = state.copy(items = items),
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    columns = columns,
                    gutter = layout.gutter,
                    spacing = layout.spacing,
                    gridState = gridState,
                    bottomPadding = padding.calculateBottomPadding(),
                    preloadsOf = { (it as? ProfileItem.PostItem)?.post?.let(postPreloads).orEmpty() },
                    emptyText = "Nothing to show",
                    header = {
                        LargeHeader(
                            title = name,
                            leading = { Avatar(name, user?.iconUrl, 56.dp) },
                            overline = user?.let { u ->
                                {
                                    Text(
                                        "${u.linkKarma + u.commentKarma} karma",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            ChipMenu(tab.label, Icons.Outlined.Person, UserWhere.entries.filter { isSelf || !it.ownOnly }, { it.label }, vm::setTab)
                            ChipMenu(sort.label, sortIcon(sort), UserSort.entries, { it.label }, vm::setSort)
                            HeaderChip("About", Icons.Outlined.Info, { aboutOpen = true })
                            if (canAct) HeaderChip("Message", Icons.Outlined.Email, message)
                            HeaderChip("Trophies", AppIcons.Trophy, { trophiesOpen = true })
                            if (!isSelf) HeaderChip("Tag user", AppIcons.TagUser, { tagging = true })
                            if (canAct) HeaderChip("Block", AppIcons.Block, { confirmBlock = true })
                            HeaderChip("Share", Icons.Outlined.Share, { nav.share("https://www.reddit.com/user/$name", "u/$name") })
                        }
                    },
                ) { item ->
                    when (item) {
                        is ProfileItem.PostItem -> PostListItem(item.post, showSubreddit = true, callbacks = callbacks, layout = layout, standalone = false)
                        is ProfileItem.CommentItem -> CommentCard(item.comment) {
                            nav.push(PostRoute(item.comment.postId, item.comment.id, context = 3))
                        }
                    }
                }
            }
        }
    }

    if (aboutOpen) {
        ProfileAboutSheet(
            name = name,
            user = user,
            onMessage = if (canAct) ({ aboutOpen = false; message() }) else null,
            onTrophies = { aboutOpen = false; trophiesOpen = true },
            onTag = if (!isSelf) ({ aboutOpen = false; tagging = true }) else null,
            onDismiss = { aboutOpen = false },
        )
    }
    if (trophiesOpen) TrophiesSheet(container, name, onDismiss = { trophiesOpen = false })
    if (tagging) TagUserDialog(name, onDismiss = { tagging = false })
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block u/$name?") },
            text = { Text("You won't see their posts and comments, and they can't message you.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    vm.block()
                }) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
        )
    }
}

private fun sortIcon(sort: UserSort): ImageVector = when (sort) {
    UserSort.NEW -> AppIcons.Burst
    UserSort.HOT -> AppIcons.Flame
    UserSort.TOP_WEEK, UserSort.TOP_ALL -> AppIcons.Bars
}

@Composable
private fun SortMenu(onPick: (UserSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UserSort.entries.forEach { sort ->
                DropdownMenuItem(text = { Text(sort.label) }, onClick = {
                    open = false
                    onPick(sort)
                })
            }
        }
    }
}

@Composable
private fun Avatar(name: String, url: String?, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        url?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

/** Sync's user About: the banner and avatar, link karma, comment karma and cake day, Message / Trophies / Tag user, then the description. */
@Composable
private fun ProfileAboutSheet(
    name: String,
    user: UserProfile?,
    onMessage: (() -> Unit)?,
    onTrophies: () -> Unit,
    onTag: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = null) {
        if (user == null) {
            SheetHandle()
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        AboutHeader(user.bannerUrl, user.iconUrl, user.name.ifEmpty { name }) {
            AboutStat(AppIcons.Link, user.linkKarma.toString())
            AboutStat(AppIcons.Comment, user.commentKarma.toString())
            // Sync writes the cake day as "21 Apr 2012".
            AboutStat(Icons.Outlined.DateRange, SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(user.createdUtc * 1000)))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            val chip = MaterialTheme.colorScheme.surfaceVariant
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                onMessage?.let { HeaderChip("Message", Icons.Outlined.Email, it, container = chip) }
                HeaderChip("Trophies", AppIcons.Trophy, onTrophies, container = chip)
                onTag?.let { HeaderChip("Tag user", AppIcons.TagUser, it, container = chip) }
            }
            Surface(color = chip, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    user.description ?: "This user has no public description",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** Sync's Trophies sheet: each trophy's icon and name. */
@Composable
private fun TrophiesSheet(container: AppContainer, name: String, onDismiss: () -> Unit) {
    val trophies by produceState<Result<List<Trophy>>?>(null, name) {
        value = runCatching { container.api.trophies(name) }.onFailure { if (it is CancellationException) throw it }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Trophies", topPadding = 0.dp)
            when (val result = trophies) {
                null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val list = result.getOrDefault(emptyList())
                    val note = result.exceptionOrNull()?.let { "Couldn't load trophies: ${it.message}" } ?: "No trophies yet".takeIf { list.isEmpty() }
                    note?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        list.forEach { trophy ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(28.dp)) { trophy.iconUrl?.let { AsyncImage(it, null, modifier = Modifier.matchParentSize()) } }
                                Spacer(Modifier.width(16.dp))
                                Text(trophy.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
