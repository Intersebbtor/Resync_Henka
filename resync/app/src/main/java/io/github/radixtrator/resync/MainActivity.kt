package io.github.radixtrator.resync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
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
import io.github.radixtrator.resync.ui.theme.ResyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ResyncApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            ResyncTheme(settings) { ResyncNavHost(container) }
        }
    }
}

@Composable
private fun ResyncNavHost(container: AppContainer) {
    val backStack = rememberNavBackStack(if (container.isConfigured) homeRoute(container) else SetupRoute)
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val username by container.username.collectAsStateWithLifecycle()
    val visited by container.visited.ids.collectAsStateWithLifecycle()
    val currentSettings by rememberUpdatedState(settings)
    val navigator = remember(backStack) { Navigator(context, backStack) { currentSettings } }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalUriHandler provides navigator,
        LocalSettings provides settings,
        LocalAccount provides username,
        LocalVisited provides visited,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = navigator::pop,
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
                entry<ImageRoute> { route -> ImageViewerScreen(route.urls, route.start, onClose = navigator::pop) }
                entry<VideoRoute> { route -> VideoPlayerScreen(route.url, route.loop, onClose = navigator::pop) }
                entry<UserRoute> { route -> ProfileScreen(route.name, route.tab, container) }
                entry<InboxRoute> { route -> InboxScreen(route, container) }
                entry<SearchRoute> { route -> SearchScreen(route.query, route.subreddit, container) }
                entry<SubmitRoute> { route -> SubmitScreen(route.subreddit, container) }
                entry<ComposeRoute> { route -> ComposeScreen(route, container) }
                entry<SettingsRoute> { SettingsScreen(container) }
            },
        )
    }
}

/** The Posts tab at launch: Explore's default feed (Sync's "Set as default"), else the front page. */
private fun homeRoute(container: AppContainer) = FeedRoute(container.explore.state.value.defaultFeed, home = true)
