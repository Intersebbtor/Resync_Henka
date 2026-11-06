package io.github.radixtrator.resync

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import io.github.radixtrator.resync.ui.common.LocalPreviousScreen
import io.github.radixtrator.resync.ui.common.PARALLAX
import io.github.radixtrator.resync.ui.common.SwipeBackState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.radixtrator.resync.notify.Notifications
import io.github.radixtrator.resync.ui.compose.ComposeScreen
import io.github.radixtrator.resync.ui.compose.SubmitScreen
import io.github.radixtrator.resync.ui.feed.FeedScreen
import io.github.radixtrator.resync.ui.inbox.InboxScreen
import io.github.radixtrator.resync.ui.login.LoginScreen
import io.github.radixtrator.resync.ui.media.ImageViewerScreen
import io.github.radixtrator.resync.ui.media.VideoPlayerScreen
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.ImageRoute
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.LocalUserTags
import io.github.radixtrator.resync.ui.navigation.LocalVisited
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.SetupRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.navigation.VideoRoute
import io.github.radixtrator.resync.ui.post.PostScreen
import io.github.radixtrator.resync.ui.profile.ProfileScreen
import io.github.radixtrator.resync.ui.search.SearchScreen
import io.github.radixtrator.resync.ui.settings.SettingsScreen
import io.github.radixtrator.resync.ui.setup.SetupScreen
import io.github.radixtrator.resync.ui.theme.BASE_FONT_SCALE
import io.github.radixtrator.resync.ui.theme.FullSizeText
import io.github.radixtrator.resync.ui.theme.ResyncTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Only fontScale is set, so every other setting still follows the device.
        applyOverrideConfiguration(Configuration().apply { fontScale = newBase.resources.configuration.fontScale * BASE_FONT_SCALE })
    }

    /** A page to open, from a tapped notification (a watched community's post, an inbox message). */
    private var openRoute by mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute = Notifications.routeFrom(intent)
        val container = (application as ResyncApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            ResyncTheme(settings) { ResyncNavHost(container, openRoute, onRouteOpened = { openRoute = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Notifications.routeFrom(intent)?.let { openRoute = it }
    }
}

@Composable
private fun ResyncNavHost(container: AppContainer, openRoute: NavKey?, onRouteOpened: () -> Unit) {
    val backStack = rememberNavBackStack(if (container.isConfigured) homeRoute(container) else SetupRoute)
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val username by container.username.collectAsStateWithLifecycle()
    val visited by container.visited.ids.collectAsStateWithLifecycle()
    val tags by container.tags.tags.collectAsStateWithLifecycle()
    val currentSettings by rememberUpdatedState(settings)
    val navigator = remember(backStack) { Navigator(context, backStack) { currentSettings } }
    LaunchedEffect(openRoute) {
        if (openRoute != null && container.isConfigured) navigator.push(openRoute)
        if (openRoute != null) onRouteOpened()
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalUriHandler provides navigator,
        LocalSettings provides settings,
        LocalAccount provides username,
        LocalVisited provides visited,
        LocalUserTags provides tags,
        LocalContainer provides container,
    ) {
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<SetupRoute> {
                    SetupScreen(
                        container = container,
                        canGoBack = navigator.canPop,
                        onBack = navigator::pop,
                        onDone = { navigator.resetTo(homeRoute(container)) },
                    )
                }
                entry<LoginRoute> { LoginScreen(container = container, onFinished = navigator::pop) }
                entry<FeedRoute> { route -> FeedScreen(route, container) }
                entry<PostRoute> { route -> PostScreen(route, container) }
                entry<ImageRoute>(metadata = MediaFade) { route -> ImageViewerScreen(route.urls, route.start, route.postId, container.downloads, onClose = navigator::pop) }
                entry<VideoRoute>(metadata = MediaFade) { route -> VideoPlayerScreen(route.url, route.loop, route.postId, container.downloads, onClose = navigator::pop) }
                entry<UserRoute> { route -> ProfileScreen(route.name, route.tab, container) }
                entry<InboxRoute> { route -> InboxScreen(route, container) }
                entry<SearchRoute> { route -> SearchScreen(route.query, route.subreddit, container, communitiesTab = route.communities) }
                entry<SubmitRoute> { route -> SubmitScreen(route.subreddit, container) }
                entry<ComposeRoute> { route -> ComposeScreen(route, container) }
                // Sync's settings don't shrink with its base font size.
                entry<SettingsRoute> { FullSizeText { SettingsScreen(container) } }
            },
        )
        LaunchedEffect(backStack.size) { SwipeBackState.popping = false }
        val previous: (@Composable () -> Unit)? = entries.getOrNull(entries.size - 2)?.let { entry -> { entry.Content() } }
        CompositionLocalProvider(LocalPreviousScreen provides previous) {
            NavDisplay(
                entries = entries,
                onBack = navigator::pop,
                transitionSpec = { cardPush() },
                popTransitionSpec = { if (SwipeBackState.popping) noTransition() else cardPop() },
                predictivePopTransitionSpec = { cardPop() },
            )
        }
    }
}

/** Media viewers fade in and out; they have their own drag-to-dismiss. */
private val MediaFade = NavDisplay.transitionSpec { ContentTransform(fadeIn(), fadeOut()) } +
    NavDisplay.popTransitionSpec { ContentTransform(fadeIn(), fadeOut(), targetContentZIndex = -1f) } +
    NavDisplay.predictivePopTransitionSpec { ContentTransform(fadeIn(), fadeOut(), targetContentZIndex = -1f) }

private val CardSpec = tween<IntOffset>(320, easing = FastOutSlowInEasing)

/** Sync's card push: the new screen slides over the old one, which drifts left and dims. */
private fun cardPush() = ContentTransform(
    targetContentEnter = slideInHorizontally(CardSpec) { it },
    initialContentExit = slideOutHorizontally(CardSpec) { (-it * PARALLAX).toInt() },
)

/** The reverse: the top card slides off to the right and the screen under it drifts back into place. */
private fun cardPop() = ContentTransform(
    targetContentEnter = slideInHorizontally(CardSpec) { (-it * PARALLAX).toInt() },
    initialContentExit = slideOutHorizontally(CardSpec) { it },
    targetContentZIndex = -1f,
)

private fun noTransition() = ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = -1f)

/** The Posts tab at launch: Explore's default feed (Sync's "Set as default"), else the front page. */
private fun homeRoute(container: AppContainer) = FeedRoute(container.explore.state.value.defaultFeed, home = true)
