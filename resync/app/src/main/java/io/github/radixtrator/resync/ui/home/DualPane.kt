package io.github.radixtrator.resync.ui.home

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import io.github.radixtrator.resync.ui.common.LocalPreviousScreen
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.Navigator

/*
 * Tablet mode's dual pane (Sync's "Dual pane"): a post opened from a feed shows on the right with the
 * feed kept on the left, instead of covering it. It's a Navigation 3 scene over the normal back stack
 * [..., FeedRoute, PostRoute], so back, predictive back and the saved state of both screens work as
 * usual: back closes the post and the feed goes full width again.
 */

/**
 * Window width (dp) from which Tablet mode shows the dual pane: Material's "expanded" width class, so
 * landscape tablets and unfolded foldables held sideways, not portrait tablets (600-839dp), which keep
 * the rail but open posts full screen. Sync's own switches for this are per orientation ("Dual pane
 * (landscape)" on, "(portrait)" off), which this width roughly matches.
 */
const val DUAL_PANE_MIN_WIDTH_DP = 840

/** The feed's share of the width, and the narrowest it gets. The post takes the rest. */
private const val LIST_FRACTION = 0.4f
private val LIST_MIN_WIDTH = 360.dp

private const val LIST_PANE_KEY = "resync.dualPane.list"
private const val DETAIL_PANE_KEY = "resync.dualPane.detail"

/** Entry metadata for a screen that can be the dual pane's left side (feeds). */
val DualPaneList: Map<String, Any> = mapOf(LIST_PANE_KEY to true)

/** Entry metadata for a screen that opens on the dual pane's right side (posts). */
val DualPaneDetail: Map<String, Any> = mapOf(DETAIL_PANE_KEY to true)

/**
 * The scene strategies for NavDisplay: the dual pane while Tablet mode is on and the window is at
 * least [DUAL_PANE_MIN_WIDTH_DP] wide, else one screen at a time.
 */
@Composable
fun rememberDualPaneSceneStrategies(): List<SceneStrategy<NavKey>> {
    val navigator = LocalNavigator.current
    val enabled = LocalSettings.current.tabletMode && LocalConfiguration.current.screenWidthDp >= DUAL_PANE_MIN_WIDTH_DP
    return remember(enabled, navigator) {
        if (enabled) listOf(DualPaneSceneStrategy(navigator.listPane)) else listOf(SinglePaneSceneStrategy())
    }
}

/**
 * Puts the top post beside the feed under it. Posts opened from the post itself (a link in a comment)
 * stack up on the right; the feed stays on the left until they're all closed.
 */
private class DualPaneSceneStrategy(private val listNavigator: Navigator) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val detail = entries.lastOrNull() ?: return null
        if (DETAIL_PANE_KEY !in detail.metadata) return null
        val listIndex = entries.indexOfLast { DETAIL_PANE_KEY !in it.metadata }
        if (listIndex < 0 || LIST_PANE_KEY !in entries[listIndex].metadata) return null
        return DualPaneScene(
            list = entries[listIndex],
            detail = detail,
            belowList = entries.getOrNull(listIndex - 1),
            previousEntries = entries.dropLast(1),
            listNavigator = listNavigator,
        )
    }
}

private class DualPaneScene(
    val list: NavEntry<NavKey>,
    val detail: NavEntry<NavKey>,
    val belowList: NavEntry<NavKey>?,
    override val previousEntries: List<NavEntry<NavKey>>,
    val listNavigator: Navigator,
) : Scene<NavKey> {
    // Keyed by the feed alone, so opening another post from it swaps the right side in place.
    override val key: Any = list.contentKey

    override val entries: List<NavEntry<NavKey>> = listOf(list, detail)

    // The feed doesn't move, so no card slide in or out of the dual pane (it would drag the feed
    // along); pages opened over it, like media or profiles, still use their own transitions.
    override val metadata: Map<String, Any> = NoTransition

    override val content: @Composable () -> Unit = { DualPane(list, detail, belowList, listNavigator) }

    override fun equals(other: Any?): Boolean =
        other is DualPaneScene && list == other.list && detail == other.detail &&
            belowList == other.belowList && previousEntries == other.previousEntries && listNavigator === other.listNavigator

    override fun hashCode(): Int = ((list.hashCode() * 31 + detail.hashCode()) * 31 + previousEntries.hashCode()) * 31 + listNavigator.hashCode()
}

private val NoTransition: Map<String, Any> =
    NavDisplay.transitionSpec { ContentTransform(EnterTransition.None, ExitTransition.None) } +
        NavDisplay.popTransitionSpec { ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = -1f) } +
        NavDisplay.predictivePopTransitionSpec { ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = -1f) }

@Composable
private fun DualPane(list: NavEntry<NavKey>, detail: NavEntry<NavKey>, belowList: NavEntry<NavKey>?, listNavigator: Navigator) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * LIST_FRACTION).coerceAtLeast(LIST_MIN_WIDTH)
        // Sliding the feed back (when it's a pushed page) reveals the screen under it, inside its pane.
        val underList: (@Composable () -> Unit)? = belowList?.let { entry -> { entry.Content() } }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight().clipToBounds()) {
                CompositionLocalProvider(
                    LocalNavigator provides listNavigator,
                    LocalUriHandler provides listNavigator,
                    LocalPreviousScreen provides underList,
                ) { list.Content() }
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                // The screen under the post is the feed, already showing on the left: drawing it again
                // behind a swipe-back would compose it twice. A swipe just slides the post away.
                CompositionLocalProvider(LocalPreviousScreen provides null) { detail.Content() }
            }
        }
    }
}
