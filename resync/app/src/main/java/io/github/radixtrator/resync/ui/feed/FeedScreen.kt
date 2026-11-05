@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(subreddit: String?, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { FeedViewModel(container, subreddit) }
    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val about by vm.about.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val isRoot = !nav.canPop
    val callbacks = remember(vm) { vm.postCallbacks() }
    val posts = state.items.visiblePosts { it }

    MessageEffect(state.message, snackbar, vm::messageShown)

    ModalNavigationDrawer(
        drawerState = drawerState,
        // On pushed feeds a right-swipe means "back", not "open drawer".
        gesturesEnabled = isRoot || drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                container = container,
                current = subreddit,
                subscriptions = subscriptions,
                close = { scope.launch { drawerState.close() } },
                onPick = { picked ->
                    if (picked != subreddit) {
                        if (picked == null) nav.resetTo(FeedRoute()) else nav.push(FeedRoute(picked))
                    }
                },
            )
        },
    ) {
        SwipeBack(enabled = !isRoot && LocalSettings.current.swipeBack, onBack = nav::pop) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (!isRoot) {
                                IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") }
                            }
                        },
                        title = {
                            Column {
                                Text(subreddit?.let { "r/$it" } ?: "Home", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(sort.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            IconButton(onClick = { nav.push(SearchRoute(subreddit = subreddit.takeIf { vm.isCommunity })) }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                            if (vm.isCommunity) {
                                IconButton(onClick = {
                                    vm.loadAbout()
                                    aboutOpen = true
                                }) { Icon(Icons.Default.Info, "About this community") }
                            }
                            SortMenu(options = vm.sortOptions, onPick = vm::setSort)
                            OverflowMenu(
                                onRefresh = vm::refresh,
                                onSubmit = { nav.push(SubmitRoute(subreddit.takeIf { vm.isCommunity })) },
                                onSettings = { nav.push(SettingsRoute) },
                            )
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
                        state = state.copy(items = posts),
                        key = { it.fullname },
                        onLoadMore = vm::loadMore,
                        onRetry = vm::retry,
                        emptyText = "No posts here",
                    ) { post ->
                        PostListItem(post, showSubreddit = !vm.isCommunity, callbacks = callbacks)
                    }
                }
            }
        }
    }

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
private fun SortMenu(options: List<Sort>, onPick: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { sort ->
                DropdownMenuItem(text = { Text(sort.label) }, onClick = { open = false; onPick(sort) })
            }
        }
    }
}

@Composable
private fun OverflowMenu(onRefresh: () -> Unit, onSubmit: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Refresh") }, onClick = { open = false; onRefresh() })
            DropdownMenuItem(text = { Text("Create post") }, onClick = { open = false; onSubmit() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onSettings() })
        }
    }
}
