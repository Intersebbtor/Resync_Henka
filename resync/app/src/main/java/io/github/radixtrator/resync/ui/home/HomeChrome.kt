@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.UserWhere
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.QuickAction
import io.github.radixtrator.resync.ui.common.QuickActionGrid
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.theme.LocalDarkTheme

/** Sync's bottom-navigation tabs. Explore isn't a page: it opens [ExploreSheet] over the current one. */
enum class HomeTab { POSTS, INBOX }

private val NavBarHeight = 80.dp
private val FabSize = 56.dp

// Sync's nav labels are larger than M3's 12sp.
private val NavLabel @Composable get() = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)

/**
 * The frame shared by Sync-style top-level pages (feeds, inbox): a toolbar that hides on scroll with
 * the account avatar (or a back arrow) and a ⋮ that drops the More actions sheet; on [home] pages the
 * bottom navigation; and the ⋯ FAB (tap: More actions, long-press: jump to a subreddit).
 *
 * [content] gets the padding its list should use so items clear the toolbar, nav bar and FAB.
 */
@Composable
fun ShellScaffold(
    container: AppContainer,
    home: Boolean,
    tab: HomeTab,
    smallTitle: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbar: SnackbarHostState,
    quickActions: List<QuickAction>,
    onReselectTab: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNavigator.current
    val inboxCount by container.inboxCount.collectAsStateWithLifecycle()
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var fabMoreOpen by rememberSaveable { mutableStateOf(false) }
    var explore by rememberSaveable { mutableStateOf<ExploreMode?>(null) }
    // The nav bar and FAB slide away with the toolbar, as Sync's autohide does.
    val hidden = scrollBehavior.state.collapsedFraction
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (home) NavBarHeight + navInset else navInset)) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (home) {
                        AccountButton(container, onClick = { accountOpen = true })
                    } else {
                        IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                title = {
                    if (smallTitle != null) Text(smallTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    actions()
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            content(
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    // Room for the last item to scroll clear of the nav bar and the FAB.
                    bottom = (if (home) NavBarHeight else 0.dp) + navInset + FabSize + 24.dp,
                ),
            )
            MoreFab(
                onClick = { fabMoreOpen = true },
                onLongClick = { explore = ExploreMode.SEARCH },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 16.dp + navInset + if (home) NavBarHeight else 0.dp)
                    .graphicsLayer {
                        val travel = 16.dp.toPx() + FabSize.toPx() + if (home) NavBarHeight.toPx() + navInset.toPx() else navInset.toPx()
                        translationY = hidden * travel
                    },
            )
            if (home) {
                HomeNavigationBar(
                    tab = tab,
                    inboxCount = inboxCount,
                    onPosts = { if (tab == HomeTab.POSTS) onReselectTab() else nav.popWhile { it is InboxRoute } },
                    onExplore = { explore = ExploreMode.BROWSE },
                    onInbox = { if (tab == HomeTab.INBOX) onReselectTab() else nav.push(InboxRoute(home = true)) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { translationY = hidden * size.height },
                )
            }
        }
    }

    if (accountOpen) AccountCard(container, onDismiss = { accountOpen = false })
    if (moreOpen) {
        TopSheet(onDismiss = { moreOpen = false }) {
            SheetTitle("More actions")
            QuickActionGrid(quickActions, onPicked = { moreOpen = false })
        }
    }
    if (fabMoreOpen) {
        ModalBottomSheet(onDismissRequest = { fabMoreOpen = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetTitle("More actions", Modifier.padding(top = 0.dp))
                QuickActionGrid(quickActions, onPicked = { fabMoreOpen = false })
            }
        }
    }
    explore?.let { mode ->
        ExploreSheet(
            container = container,
            mode = mode,
            onDismiss = { explore = null },
            onOpenFeed = { name ->
                explore = null
                openFeedFromExplore(nav, name)
            },
        )
    }
}

/**
 * Explore swaps the Posts tab's feed, keeping back history, like Sync; the front page resets to the root.
 * From a non-home page (a pushed feed or the inbox) the pick still lands on the Posts tab.
 */
private fun openFeedFromExplore(nav: Navigator, name: String?) {
    val current = nav.top
    if (current is FeedRoute && current.home && current.subreddit.equals(name, ignoreCase = true)) return
    if (name == null) nav.resetTo(FeedRoute(home = true)) else nav.push(FeedRoute(name, home = true))
}

/**
 * The More actions grid, in Sync's default order (Search, Profile, About, Submit, Dark mode, Settings,
 * Change view, Refresh, Saved). [onAbout] and [onChangeView] only exist on some pages.
 */
@Composable
fun quickActions(
    container: AppContainer,
    onRefresh: () -> Unit,
    subreddit: String? = null,
    onAbout: (() -> Unit)? = null,
    onChangeView: (() -> Unit)? = null,
): List<QuickAction> {
    val nav = LocalNavigator.current
    val me = LocalAccount.current
    val dark = LocalDarkTheme.current
    return buildList {
        add(QuickAction("Search", Icons.Outlined.Search) { nav.push(SearchRoute(subreddit = subreddit)) })
        if (me != null) add(QuickAction("Profile", Icons.Outlined.Person) { nav.push(UserRoute(me)) })
        onAbout?.let { add(QuickAction("About", Icons.Outlined.Info, onClick = it)) }
        add(QuickAction("Submit", Icons.Outlined.Create) { nav.push(SubmitRoute(subreddit)) })
        add(QuickAction("Dark mode", AppIcons.Moon, active = dark) {
            container.settings.update { it.copy(theme = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }
        })
        add(QuickAction("Settings", Icons.Outlined.Settings) { nav.push(SettingsRoute) })
        onChangeView?.let { add(QuickAction("Change view", AppIcons.ViewStack, onClick = it)) }
        add(QuickAction("Refresh", Icons.Outlined.Refresh, onClick = onRefresh))
        if (me != null) add(QuickAction("Saved", AppIcons.Bookmark) { nav.push(UserRoute(me, UserWhere.SAVED)) })
    }
}

@Composable
private fun HomeNavigationBar(
    tab: HomeTab,
    inboxCount: Int,
    onPosts: () -> Unit,
    onExplore: () -> Unit,
    onInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sync keeps unselected items at full contrast; only the pill marks the current tab.
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
    )
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        NavItem("Posts", if (tab == HomeTab.POSTS) Icons.Filled.Home else Icons.Outlined.Home, tab == HomeTab.POSTS, onPosts, colors)
        NavItem("Explore", AppIcons.Compass, false, onExplore, colors)
        NavigationBarItem(
            selected = tab == HomeTab.INBOX,
            onClick = onInbox,
            colors = colors,
            label = { Text("Inbox", style = NavLabel) },
            icon = {
                BadgedBox(badge = { if (inboxCount > 0) Badge { Text("$inboxCount") } }) {
                    Icon(if (tab == HomeTab.INBOX) Icons.Filled.Email else Icons.Outlined.Email, null)
                }
            },
        )
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    colors: androidx.compose.material3.NavigationBarItemColors,
) {
    NavigationBarItem(selected = selected, onClick = onClick, colors = colors, label = { Text(label, style = NavLabel) }, icon = { Icon(icon, null) })
}

/** Sync's FAB: a rounded square with ⋯. */
@Composable
private fun MoreFab(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 3.dp,
        modifier = modifier.size(FabSize),
    ) {
        Box(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MoreVert, "More actions", modifier = Modifier.rotate(90f))
        }
    }
}

@Composable
private fun AccountButton(container: AppContainer, onClick: () -> Unit) {
    val avatar by container.avatar.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) { Avatar(avatar, size = 30) }
}

@Composable
private fun Avatar(url: String?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "Account", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.AccountCircle, "Account", modifier = Modifier.size((size * 0.8f).dp))
        }
    }
}

/** The account switcher Sync shows under the avatar: accounts, add account, then profile and settings. */
@Composable
private fun AccountCard(container: AppContainer, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val me by container.username.collectAsStateWithLifecycle()
    val accounts by container.accounts.collectAsStateWithLifecycle()
    val avatar by container.avatar.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    fun go(action: () -> Unit) {
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = null)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 56.dp)
                    .fillMaxWidth()
                    .clickable(onClick = {}, indication = null, interactionSource = null),
            ) {
                Column {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(avatar, size = 48)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            me ?: "Anonymous",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (me != null) IconButton(onClick = { confirmLogout = true }) { Icon(AppIcons.RemoveCircle, "Log out") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    accounts.filter { it != me }.forEach { name ->
                        CardRow(name, Icons.Outlined.AccountCircle) { go { container.switchAccount(name) } }
                    }
                    if (me != null) CardRow("Anonymous", Icons.Outlined.AccountCircle) { go { container.switchAccount(null) } }
                    CardRow("Add account", AppIcons.PersonAdd) { go { nav.push(LoginRoute) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    me?.let { name -> CardRow("Profile", Icons.Outlined.Person) { go { nav.push(UserRoute(name)) } } }
                    CardRow("Settings", Icons.Outlined.Settings) { go { nav.push(SettingsRoute) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Resync ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out u/$me?") },
            text = { Text("Resync forgets this account on this device.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; go(container::logout) }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CardRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
