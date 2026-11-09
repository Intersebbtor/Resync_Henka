package io.github.radixtrator.resync.ui.media

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The screen the image viewer was opened from, captured just before it opens, so the viewer can sit on a
 * blurred copy of it. Navigation pages are drawn one at a time, so the page underneath can't be drawn live.
 */
object ScreenSnapshot {
    /** A small (1/8 size) copy of the last capture; small enough that upscaling it already blurs it. */
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    internal var capture: (() -> Unit)? = null

    /** Called by Navigator before it pushes an image viewer. */
    fun capture() = capture?.invoke()

    internal fun set(full: ImageBitmap) {
        val src = full.asAndroidBitmap()
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        bitmap = Bitmap.createScaledBitmap(soft, (soft.width / 8).coerceAtLeast(1), (soft.height / 8).coerceAtLeast(1), true).asImageBitmap()
    }
}

/** Records [content] (the app's pages) so [ScreenSnapshot.capture] can copy what it last drew. */
@Composable
fun ScreenSnapshotHost(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    ScreenSnapshot.capture = {
        // Undispatched, so the copy is made from what's on screen now, before the viewer draws over it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val full = runCatching { layer.toImageBitmap() }.getOrNull() ?: return@launch
            withContext(Dispatchers.Default) { runCatching { ScreenSnapshot.set(full) } }
        }
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
    ) { content() }
}

/**
 * [ScreenSnapshot] stretched over the screen and blurred: the small copy upscaled with bilinear filtering
 * is soft on every API level, and [blur] (API 31+) smooths it out. Dimmed so the image stands out.
 */
@Composable
fun BlurredScreenBackdrop(modifier: Modifier = Modifier) {
    val bitmap = ScreenSnapshot.bitmap ?: return
    Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.Low,
        modifier = modifier
            .fillMaxSize()
            .blur(24.dp, BlurredEdgeTreatment.Rectangle)
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.35f))
            },
    )
}
