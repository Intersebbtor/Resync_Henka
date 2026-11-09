package io.github.radixtrator.resync.ui.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/** How far media can be pinched in. */
private const val MAX_ZOOM = 20f

/** Where a double tap zooms to from 1x. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Pinch/double-tap zoom state shared by the image viewer and the video player. */
@Stable
internal class ZoomState(private val onZoomChange: (Boolean) -> Unit = {}) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    val zoomed: Boolean get() = scale > 1f

    fun zoomTo(value: Float) {
        val wasZoomed = zoomed
        scale = value.coerceIn(1f, MAX_ZOOM)
        if (scale <= 1f) offset = Offset.Zero
        if (zoomed != wasZoomed) onZoomChange(zoomed)
    }

    fun pan(by: Offset) {
        if (zoomed) offset += by
    }

    /** Double tap: back to 1x when zoomed, otherwise in to [DOUBLE_TAP_ZOOM]. */
    fun toggle() = zoomTo(if (zoomed) 1f else DOUBLE_TAP_ZOOM)
}

/**
 * Taps, double taps and pinches for [state]. Single-finger drags are left alone at 1x (for a pager or
 * swipe-to-dismiss) and pan once zoomed.
 */
internal fun Modifier.zoomGestures(state: ZoomState, onTap: () -> Unit): Modifier = this
    .pointerInput(state) {
        detectTapGestures(onTap = { onTap() }, onDoubleTap = { state.toggle() })
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size > 1 || state.zoomed) {
                    state.zoomTo(state.scale * event.calculateZoom())
                    state.pan(event.calculatePan())
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** Draws the content scaled and panned by [state]. */
internal fun Modifier.zoomTransform(state: ZoomState): Modifier = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    translationX = state.offset.x
    translationY = state.offset.y
}
