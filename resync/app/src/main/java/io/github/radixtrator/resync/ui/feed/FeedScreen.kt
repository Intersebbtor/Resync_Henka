@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.SortKind
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.data.TimeRange
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.PagedGrid
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.common.columns
import io.github.radixtrator.resync.ui.common.gutter
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.spacing
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.home.HomeTab
import io.github.radixtrator.resync.ui.home.ShellScaffold
import io.github.radixtrator.resync.ui.home.quickActions
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(route: FeedRoute, container: AppContainer) {
    val subreddit = route.subreddit
    val nav = LocalNavigator.current
    val vm = viewModel { FeedViewModel(container, subreddit) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val about by vm.about.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyStaggeredGridState()
    val settings = LocalSettings.current
    val layout = settings.layoutFor(subreddit)
    val columns = layout.columns(LocalConfiguration.current.screenWidthDp)
    val scope = rememberCoroutineScope()
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var viewOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val callbacks = remember(vm) { vm.postCallbacks() }
    val posts = state.items.visiblePosts { it }
    val title = Format.feedName(subreddit)
    // The toolbar shows the title only once the big header has scrolled away.
    val headerGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val openAbout = {
        vm.loadAbout()
        aboutOpen = true
    }

    MessageEffect(state.message, snackbar, vm::messageShown)

    SwipeBack(enabled = !route.home && LocalSettings.current.swipeBack, onBack = nav::pop) {
        ShellScaffold(
            container = container,
            home = route.home,
            tab = HomeTab.POSTS,
            smallTitle = title.takeIf { headerGone },
            scrollBehavior = scrollBehavior,
            snackbar = snackbar,
            quickActions = quickActions(
                container,
                onRefresh = vm::refresh,
                subreddit = subreddit.takeIf { vm.isCommunity },
                onAbout = if (vm.isCommunity) openAbout else null,
                onChangeView = { viewOpen = true },
            ),
            onReselectTab = { scope.launch { gridState.animateScrollToItem(0) } },
            actions = { IconButton(onClick = { sortOpen = true }) { Icon(AppIcons.Sort, "Sort") } },
            showSearch = true,
            searchSubreddit = subreddit.takeIf { vm.isCommunity },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            ) {
                PagedGrid(
                    state = state.copy(items = posts),
                    key = { it.fullname },
                    onLoadMore = vm::loadMore,
                    onRetry = vm::retry,
                    columns = columns,
                    gutter = layout.gutter,
                    spacing = layout.spacing,
                    gridState = gridState,
                    bottomPadding = padding.calculateBottomPadding(),
                    emptyText = "No posts here",
                    header = {
                        FeedHeader(
                            title = title,
                            about = about.takeIf { vm.isCommunity },
                            sort = sort,
                            isCommunity = vm.isCommunity,
                            onSort = { sortOpen = true },
                            onChangeView = { viewOpen = true },
                            onJoin = vm::toggleSubscribe,
                            onAbout = openAbout,
                        )
                    },
                ) { post ->
                    PostListItem(post, showSubreddit = !vm.isCommunity, callbacks = callbacks, layout = layout, standalone = false)
                }
            }
        }
    }

    if (sortOpen) SortSheet(current = sort, options = vm.sortOptions, onDismiss = { sortOpen = false }, onPick = vm::setSort)
    if (viewOpen) ChangeViewSheet(container, subreddit, current = layout, onDismiss = { viewOpen = false })
    if (aboutOpen) {
        SubredditInfoSheet(
            about = about,
            onDismiss = { aboutOpen = false },
            onToggleSubscribe = vm::toggleSubscribe,
            onSubmit = {
                aboutOpen = false
                nav.push(SubmitRoute(subreddit))
            },
        )
    }
}

@Composable
private fun FeedHeader(
    title: String,
    about: Subreddit?,
    sort: Sort,
    isCommunity: Boolean,
    onSort: () -> Unit,
    onChangeView: () -> Unit,
    onJoin: () -> Unit,
    onAbout: () -> Unit,
) {
    LargeHeader(
        title = title,
        leading = if (isCommunity) {
            { CommunityIcon(about, title) }
        } else null,
        overline = about?.let { sub ->
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(Format.count(sub.subscribers), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        HeaderChip(sort.label, sortIcon(sort.kind), onSort, dropdown = true)
        if (isCommunity) {
            val joined = about?.isSubscribed == true
            HeaderChip(if (joined) "Joined" else "Join", if (joined) Icons.Outlined.Check else Icons.Outlined.Add, onJoin)
            HeaderChip("About", Icons.Outlined.Info, onAbout)
        }
        HeaderChip("Change view", AppIcons.ViewStack, onChangeView)
    }
}

@Composable
private fun CommunityIcon(about: Subreddit?, name: String) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        about?.iconUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private fun sortIcon(kind: SortKind): ImageVector = when (kind) {
    SortKind.BEST -> Icons.Outlined.Star
    SortKind.HOT -> AppIcons.Flame
    SortKind.NEW -> AppIcons.Burst
    SortKind.RISING -> AppIcons.TrendingUp
    SortKind.TOP -> AppIcons.Bars
    SortKind.CONTROVERSIAL -> AppIcons.TrendingDown
}

/**
 * Sync's sort drop-down. Top and Controversial open their time ranges in place, with a "..." row
 * to step back out, as Sync does.
 */
@Composable
private fun SortSheet(current: Sort, options: List<SortKind>, onDismiss: () -> Unit, onPick: (Sort) -> Unit) {
    var timedKind by remember { mutableStateOf<SortKind?>(null) }
    fun pick(sort: Sort) {
        onDismiss()
        onPick(sort)
    }
    TopSheet(onDismiss = onDismiss) {
        val kind = timedKind
        if (kind == null) {
            SheetTitle("Subreddit sort")
            options.forEach { option ->
                OptionRow(
                    label = option.label,
                    icon = sortIcon(option),
                    selected = option == current.kind,
                    onClick = { if (option.timed) timedKind = option else pick(Sort(option)) },
                    trailing = if (option.timed) {
                        { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(end = 8.dp)) }
                    } else null,
                )
            }
        } else {
            SheetTitle(kind.label)
            OptionRow(label = "...", icon = null, onClick = { timedKind = null })
            TimeRange.entries.forEach { range ->
                OptionRow(
                    label = range.label,
                    icon = null,
                    selected = current.kind == kind && current.time == range,
                    onClick = { pick(Sort(kind, range)) },
                )
            }
        }
    }
}

/** Sync's Change view: picks this feed's layout (other feeds keep theirs). */
@Composable
private fun ChangeViewSheet(container: AppContainer, subreddit: String?, current: PostLayout, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Change view", topPadding = 0.dp)
            PostLayout.entries.forEach { layout ->
                OptionRow(
                    label = layout.label,
                    icon = null,
                    onClick = {
                        container.settings.update { it.copy(feedLayouts = it.feedLayouts + (Settings.feedKey(subreddit) to layout)) }
                        onDismiss()
                    },
                    radio = layout == current,
                )
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Cancel")
            }
        }
    }
}
