package io.github.radixtrator.resync.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How many columns the feed around a post has, so card previews load a rendition about as wide as a column. */
val LocalFeedColumns = staticCompositionLocalOf { 1 }

/**
 * The image request for a feed preview of [url]. With [blurWidth] (NSFW and spoiler previews) the image is blurred
 * once as it's decoded, down to that many pixels across, rather than with a blur effect redrawn on every frame of a
 * scroll; drawn back up to size, the tiny image reads as a soft blur.
 */
@Composable
fun rememberPreviewRequest(url: String, blurWidth: Int? = null): Any {
    val context = LocalPlatformContext.current
    return remember(url, blurWidth) {
        if (blurWidth == null) url else ImageRequest.Builder(context).data(url).transformations(BlurTransformation(blurWidth)).build()
    }
}

/**
 * An image a list item will show: [url] drawn [width] by [height] pixels, cropped to fill. [decode] false (blurred
 * previews, whose memory-cache entry depends on the exact drawn size) only fetches it to the disk cache.
 */
data class ImagePreload(val url: String, val width: Int, val height: Int, val decode: Boolean = true)

/**
 * Loads the images of the items just past the screen before they scroll in, so scrolling doesn't land on empty
 * frames that then fade in. [lastVisible] is the index in [items] of the last item on screen. The next [decodeAhead]
 * items are fetched and decoded into the memory cache at the size they'll be drawn, so they show up already loaded;
 * the [fetchAhead] items after those are only downloaded to the disk cache, leaving a quick decode rather than a
 * network round trip. Each URL is requested once per list.
 */
@Composable
fun <T> PreloadImages(
    items: List<T>,
    lastVisible: () -> Int,
    decodeAhead: Int,
    fetchAhead: Int,
    preloadsOf: (T) -> List<ImagePreload>,
) {
    val context = LocalPlatformContext.current
    val currentItems by rememberUpdatedState(items)
    val currentPreloads by rememberUpdatedState(preloadsOf)
    val currentLastVisible by rememberUpdatedState(lastVisible)
    val decoded = remember { HashSet<String>() }
    val fetched = remember { HashSet<String>() }
    LaunchedEffect(context, decodeAhead, fetchAhead) {
        val loader = SingletonImageLoader.get(context)
        snapshotFlow { Triple(currentLastVisible(), currentItems, currentPreloads) }
            .distinctUntilChanged()
            .collect { (last, list, preloadsFor) ->
                val first = (last + 1).coerceAtLeast(0)
                val end = min(list.size, first + decodeAhead + fetchAhead)
                for (i in first until end) {
                    for (p in preloadsFor(list[i])) {
                        if (p.decode && i < first + decodeAhead) {
                            if (decoded.add(p.url)) {
                                fetched.add(p.url)
                                // Same data, size and crop as the AsyncImage that will draw it, so that one finds it
                                // in the memory cache.
                                loader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(p.url)
                                        .size(p.width.coerceAtLeast(1), p.height.coerceAtLeast(1))
                                        .scale(Scale.FILL)
                                        .build(),
                                )
                            }
                        } else if (fetched.add(p.url)) {
                            // Download only: a tiny decode, kept out of the memory cache; the bytes stay on disk.
                            loader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(p.url)
                                    .size(PREFETCH_DECODE_SIZE)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                            )
                        }
                    }
                }
            }
    }
}

private const val PREFETCH_DECODE_SIZE = 16

/** Shrinks the image to [width] pixels across and box-blurs it there, which costs next to nothing at that size. */
private class BlurTransformation(private val width: Int) : Transformation() {
    override val cacheKey = "blur-$width"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = width.coerceAtMost(input.width).coerceAtLeast(1)
        val h = max(1, (input.height * w.toFloat() / input.width).roundToInt())
        val small = Bitmap.createScaledBitmap(input, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(2) {
            boxBlur(pixels, w, h, horizontal = true)
            boxBlur(pixels, w, h, horizontal = false)
        }
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }

    /** A 3-wide box blur along one axis, clamped at the edges. */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val line = IntArray(length)
        for (l in 0 until lines) {
            for (i in 0 until length) line[i] = pixels[if (horizontal) l * w + i else i * w + l]
            for (i in 0 until length) {
                val a = line[(i - 1).coerceAtLeast(0)]
                val b = line[i]
                val c = line[(i + 1).coerceAtMost(length - 1)]
                fun ch(shift: Int) = (((a shr shift) and 0xFF) + ((b shr shift) and 0xFF) + ((c shr shift) and 0xFF)) / 3
                pixels[if (horizontal) l * w + i else i * w + l] = (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }
}
