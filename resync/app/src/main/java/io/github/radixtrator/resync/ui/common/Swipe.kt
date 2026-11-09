package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.ui.theme.ResyncColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drag distances at which Sync's extended swipe arms each stage, measured from its screens: a short
 * drag shows the first icon unarmed, then each step arms the next action.
 */
private val StageThresholds = listOf(72.dp, 144.dp, 216.dp)

/** Index of the stage armed at [distance] (px dragged left), or -1 when none is. */
internal fun armedStage(distance: Float, thresholds: List<Float>, stageCount: Int): Int =
    thresholds.take(stageCount).indexOfLast { distance >= it }

val SwipeAction.icon: ImageVector
    get() = when (this) {
        SwipeAction.UPVOTE -> AppIcons.ArrowUp
        SwipeAction.DOWNVOTE -> AppIcons.ArrowDown
        SwipeAction.SAVE -> AppIcons.Bookmark
        SwipeAction.PROFILE -> Icons.Outlined.Person
        SwipeAction.SHARE -> Icons.Outlined.Share
        SwipeAction.HIDE -> Icons.Outlined.Close
        SwipeAction.MARK_READ -> Icons.Outlined.Check
        SwipeAction.REPLY -> AppIcons.Reply
    }

/** Panel colors; the vote, save, reply and hide ones are sampled from Sync. */
val SwipeAction.color: Color
    get() = when (this) {
        SwipeAction.UPVOTE -> ResyncColors.Upvote
        SwipeAction.DOWNVOTE -> ResyncColors.Downvote
        SwipeAction.SAVE -> ResyncColors.Saved
        SwipeAction.PROFILE -> Color(0xFF4484F6)
        SwipeAction.SHARE -> Color(0xFF26A69A)
        SwipeAction.HIDE -> ResyncColors.Hide
        SwipeAction.MARK_READ -> Color(0xFF607D8B)
        SwipeAction.REPLY -> ResyncColors.Reply
    }

/**
 * Sync's extended swipe: drag [content] left and a panel shows the action you'd get; dragging further
 * steps through [stages], with a haptic tick as each arms. Letting go runs the armed one
 * ([onAction]) and springs back; letting go before the first stage does nothing.
 *
 * Only leftward drags are claimed, so list scrolling and slide-to-go-back keep working.
 */
@Composable
fun SwipeActions(
    stages: List<SwipeAction>,
    onAction: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    if (!enabled || stages.isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholds = remember(density) { with(density) { StageThresholds.map { it.toPx() } } }
    var width by remember { mutableIntStateOf(0) }
    // The gesture outlives recompositions, so it reads the latest action (a vote flips with the post's state).
    val currentOnAction by rememberUpdatedState(onAction)
    // The row recomposes only when the panel appears or a stage arms; the drag itself moves in layout and layers.
    val armed by remember(thresholds, stages.size) { derivedStateOf { armedStage(-offset.value, thresholds, stages.size) } }
    val revealed by remember { derivedStateOf { offset.value < 0f } }

    LaunchedEffect(armed) { if (armed >= 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }

    Box(
        modifier
            .clip(shape)
            .onSizeChanged { width = it.width }
            .pointerInput(stages) {
                leftDrags(
                    scope,
                    onDelta = { offset.snapTo((offset.value + it).coerceIn(-width.toFloat(), 0f)) },
                    onEnd = {
                        val stage = armedStage(-offset.value, thresholds, stages.size)
                        if (stage >= 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnAction(stages[stage])
                        }
                        offset.animateTo(0f)
                    },
                )
            },
    ) {
        if (revealed) {
            val shown = stages[armed.coerceAtLeast(0)]
            Box(
                Modifier
                    .matchParentSize()
                    .background(if (armed >= 0) shown.color else MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                // The icon rides in the middle of the revealed strip.
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val strip = (-offset.value).roundToInt().coerceIn(0, constraints.maxWidth)
                            val placeable = measurable.measure(constraints.copy(minWidth = strip, maxWidth = strip))
                            layout(strip, placeable.height) { placeable.place(0, 0) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        shown.icon,
                        shown.label,
                        tint = if (armed >= 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}

/**
 * Claims a single-finger drag once it's clearly leftward; anything vertical (scrolling) or rightward
 * (slide back) is left alone. Runs in the Main pass, so a scroll that already took the pointer wins.
 */
private suspend fun PointerInputScope.leftDrags(
    scope: CoroutineScope,
    onDelta: suspend (Float) -> Unit,
    onEnd: suspend () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (!claimed && change.isConsumed) break
            val delta = change.positionChange()
            total += delta
            if (!claimed) {
                val slop = viewConfiguration.touchSlop
                when {
                    abs(total.y) > slop && abs(total.y) >= abs(total.x) -> break
                    total.x > slop -> break
                    -total.x > slop && -total.x > abs(total.y) * 1.5f -> claimed = true
                }
            }
            if (claimed) {
                change.consume()
                scope.launch { onDelta(delta.x) }
            }
        }
        if (claimed) scope.launch { onEnd() }
    }
}
