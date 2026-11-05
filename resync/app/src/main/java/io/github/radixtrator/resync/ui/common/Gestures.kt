package io.github.radixtrator.resync.ui.common

import androidx.compose.animation.core.Animatable
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

/** Fling media up or down to close it, like Sync's viewer. The backdrop fades as it moves. */
@Composable
fun DismissibleMedia(onDismiss: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(1) }
    val progress = (abs(offset.value) / (height * 0.5f)).coerceIn(0f, 1f)
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
                    onDelta = { offset.snapTo(offset.value + it) },
                    onEnd = { velocity ->
                        if (abs(offset.value) > height * 0.18f || abs(velocity) > 2500f) onDismiss() else offset.animateTo(0f)
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

/** Sync's "slide anywhere to go back": drag the page right to pop it. */
@Composable
fun SwipeBack(enabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
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
                            offset.animateTo(width.toFloat())
                            onBack()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                )
            }
            .graphicsLayer { translationX = offset.value },
    ) { content() }
}
