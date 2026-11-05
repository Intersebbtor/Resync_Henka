package io.github.radixtrator.resync.ui.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.github.radixtrator.resync.ui.common.DismissibleMedia

@Composable
fun ImageViewerScreen(urls: List<String>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { urls.size }
    // Zoomed pages own their drags (panning), so swipe-to-dismiss is off while zoomed.
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    DismissibleMedia(onDismiss = onClose, enabled = zoomed[pager.currentPage] != true) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(urls[page], onZoomChange = { zoomed[page] = it })
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
                )
            }
        }
    }
}

/** Pinch/double-tap zoom that leaves single-finger swipes to the pager while not zoomed. */
@Composable
private fun ZoomableImage(url: String, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun setScale(value: Float) {
        scale = value
        if (value <= 1f) offset = Offset.Zero
        onZoomChange(value > 1f)
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { setScale(if (scale > 1f) 1f else 2.5f) })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            setScale((scale * event.calculateZoom()).coerceIn(1f, 6f))
                            if (scale > 1f) offset += event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        val loadState by painter.state.collectAsState()
        when (loadState) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            is AsyncImagePainter.State.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Couldn't load image", color = Color.White) }
            else -> SubcomposeAsyncImageContent()
        }
    }
}
