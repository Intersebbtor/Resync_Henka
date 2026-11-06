package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Axis { HORIZONTAL_RIGHT, VERTICAL }

/**
 * Watches single-finger drags in the Initial pass so it can claim them before children (pagers,
 * zoomable images, a Media3 PlayerView) do, but only once the drag is clearly along [axis].
 * Reports the drag delta and, on release, the velocity along that axis.
 */
private suspend fun PointerInputScope.claimDrags(
    axis: Axis,
    scope: CoroutineScope,
    onDelta: suspend (Float) -> Unit,
    onEnd: suspend (velocity: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tracker = VelocityTracker()
        var total = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) break // pinch: leave it to the child
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            total += delta
            if (!claimed) {
                claimed = when (axis) {
                    Axis.VERTICAL -> abs(total.y) > viewConfiguration.touchSlop && abs(total.y) > abs(total.x) * 1.5f
                    Axis.HORIZONTAL_RIGHT -> total.x > viewConfiguration.touchSlop && total.x > abs(total.y) * 1.5f
                }
            }
            if (claimed) {
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                val d = if (axis == Axis.VERTICAL) delta.y else delta.x
                scope.launch { onDelta(d) }
            }
        }
        if (claimed) {
            val v = tracker.calculateVelocity()
            scope.launch { onEnd(if (axis == Axis.VERTICAL) v.y else v.x) }
        }
    }
}

/**
 * Fling media up or down to close it, like Sync's viewer. The backdrop fades as it moves. With
 * [onSwipeUp], only a downward fling closes it: swiping up calls that instead (Sync's info sheet) and
 * the media springs back.
 */
@Composable
fun DismissibleMedia(
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onSwipeUp: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(1) }
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val travel = if (onSwipeUp != null) offset.value.coerceAtLeast(0f) else abs(offset.value)
    val progress = (travel / (height * 0.5f)).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height }
            .background(Color.Black.copy(alpha = 1f - progress * 0.85f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                claimDrags(
                    Axis.VERTICAL,
                    scope,
                    // Upward drags only hint at the sheet, so they move a fraction of the finger.
                    onDelta = { offset.snapTo(offset.value + if (swipeUp != null && offset.value + it < 0f) it * 0.3f else it) },
                    onEnd = { velocity ->
                        val up = swipeUp
                        when {
                            up != null && offset.value < 0f -> {
                                if (offset.value < -height * 0.03f || velocity < -1500f) up()
                                offset.animateTo(0f)
                            }
                            abs(offset.value) > height * 0.18f || abs(velocity) > 2500f -> onDismiss()
                            else -> offset.animateTo(0f)
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    val s = 1f - progress * 0.15f
                    scaleX = s
                    scaleY = s
                },
        ) { content() }
    }
}

/** The screen beneath the current one, so a drag can reveal it like a card being slid away. Null at the root. */
val LocalPreviousScreen = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** Set just before a swipe-back pops, so the nav host skips its own pop animation (the drag already played it). */
object SwipeBackState {
    @Volatile var popping = false
}

/** Sync's "slide anywhere to go back": drag the page right like a card, revealing the previous screen. */
@Composable
fun SwipeBack(enabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val previous = LocalPreviousScreen.current
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                claimDrags(
                    Axis.HORIZONTAL_RIGHT,
                    scope,
                    onDelta = { offset.snapTo((offset.value + it).coerceAtLeast(0f)) },
                    onEnd = { velocity ->
                        if (offset.value > width * 0.33f || velocity > 1800f) {
                            offset.animateTo(width.toFloat(), tween(180, easing = LinearOutSlowInEasing))
                            SwipeBackState.popping = true
                            onBack()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                )
            },
    ) {
        val progress = (offset.value / width).coerceIn(0f, 1f)
        if (previous != null && offset.value > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -width * PARALLAX * (1f - progress) }) {
                CompositionLocalProvider(LocalPreviousScreen provides null) { previous() }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f * (1f - progress))))
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value
                    if (offset.value > 0f) shadowElevation = 24f
                }
                .background(MaterialTheme.colorScheme.background),
        ) { content() }
    }
}

/** How far the screen under a card shifts left, as a fraction of the width. */
const val PARALLAX = 0.3f
