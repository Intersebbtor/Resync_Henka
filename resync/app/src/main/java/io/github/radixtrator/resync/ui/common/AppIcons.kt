package io.github.radixtrator.resync.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few glyphs material-icons-core lacks, drawn as simple 24x24 outlines. */
object AppIcons {
    val Comment: ImageVector by lazy {
        outline("Comment") {
            moveTo(4f, 4.5f); lineTo(20f, 4.5f); lineTo(20f, 16f); lineTo(11f, 16f)
            lineTo(7f, 19.5f); lineTo(7f, 16f); lineTo(4f, 16f); close()
        }
    }

    val Bookmark: ImageVector by lazy { outline("Bookmark") { bookmark() } }
    val BookmarkFilled: ImageVector by lazy { filled("BookmarkFilled") { bookmark() } }

    val Gallery: ImageVector by lazy {
        outline("Gallery") {
            moveTo(8f, 4f); lineTo(20f, 4f); lineTo(20f, 16f); lineTo(8f, 16f); close()
            moveTo(4f, 8f); lineTo(4f, 20f); lineTo(16f, 20f)
        }
    }

    val OpenExternal: ImageVector by lazy {
        outline("OpenExternal") {
            moveTo(13f, 5f); lineTo(5f, 5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 11f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 4f); lineTo(11f, 13f)
        }
    }

    val Reply: ImageVector by lazy {
        outline("Reply") {
            moveTo(9f, 5f); lineTo(4f, 10f); lineTo(9f, 15f)
            moveTo(4f, 10f); lineTo(14f, 10f)
            curveTo(17.5f, 10f, 20f, 12.5f, 20f, 16f); lineTo(20f, 19f)
        }
    }

    val Sort: ImageVector by lazy {
        outline("Sort") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(15f, 12f)
            moveTo(4f, 18f); lineTo(10f, 18f)
        }
    }

    // Sort sheet glyphs, after Sync's: flame (Hot), burst (New), trend up (Rising), bars (Top), trend down (Controversial).
    val Flame: ImageVector by lazy {
        outline("Flame") {
            moveTo(12f, 3f); curveTo(15f, 6f, 18f, 9.5f, 18f, 14f); curveTo(18f, 17.3f, 15.3f, 20.5f, 12f, 20.5f)
            curveTo(8.7f, 20.5f, 6f, 17.3f, 6f, 14f); curveTo(6f, 11.5f, 7.3f, 9.6f, 8.8f, 8.3f)
            curveTo(9f, 10.2f, 10f, 11.6f, 11.4f, 12.2f); curveTo(10.9f, 9f, 11.2f, 5.8f, 12f, 3f); close()
        }
    }

    val Burst: ImageVector by lazy {
        outline("Burst") {
            for (i in 0 until 16) {
                val r = if (i % 2 == 0) 9f else 6.2f
                val a = Math.PI * i / 8
                val x = 12f + r * kotlin.math.sin(a).toFloat()
                val y = 12f - r * kotlin.math.cos(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    val TrendingUp: ImageVector by lazy {
        outline("TrendingUp") {
            moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(21f, 7f)
            moveTo(15f, 7f); lineTo(21f, 7f); lineTo(21f, 13f)
        }
    }

    val TrendingDown: ImageVector by lazy {
        outline("TrendingDown") {
            moveTo(3f, 7f); lineTo(9f, 13f); lineTo(13f, 9f); lineTo(21f, 17f)
            moveTo(15f, 17f); lineTo(21f, 17f); lineTo(21f, 11f)
        }
    }

    val Bars: ImageVector by lazy {
        outline("Bars") {
            moveTo(5f, 20f); lineTo(5f, 12f)
            moveTo(12f, 20f); lineTo(12f, 5f)
            moveTo(19f, 20f); lineTo(19f, 9f)
        }
    }

    val Compass: ImageVector by lazy {
        outline("Compass") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(15.5f, 8.5f); lineTo(13.5f, 13.5f); lineTo(8.5f, 15.5f); lineTo(10.5f, 10.5f); close()
        }
    }

    val Moon: ImageVector by lazy {
        outline("Moon") {
            moveTo(20f, 14.5f); arcTo(8.5f, 8.5f, 0f, true, true, 9.5f, 4f); arcTo(7f, 7f, 0f, false, false, 20f, 14.5f); close()
        }
    }

    /** Two stacked rows: Sync's "Change view". */
    val ViewStack: ImageVector by lazy {
        outline("ViewStack") {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 11f); lineTo(4f, 11f); close()
            moveTo(4f, 13f); lineTo(20f, 13f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        }
    }

    val DoubleCheck: ImageVector by lazy {
        outline("DoubleCheck") {
            moveTo(2f, 12.5f); lineTo(6.5f, 17f); lineTo(15f, 8f)
            moveTo(11.5f, 15.5f); lineTo(13f, 17f); lineTo(22f, 8f)
        }
    }

    val Pin: ImageVector by lazy {
        outline("Pin") {
            moveTo(9f, 3.5f); lineTo(15f, 3.5f)
            moveTo(10f, 3.5f); lineTo(10f, 9.5f); lineTo(6.5f, 13.5f); lineTo(17.5f, 13.5f); lineTo(14f, 9.5f); lineTo(14f, 3.5f)
            moveTo(12f, 13.5f); lineTo(12f, 20.5f)
        }
    }

    /** Content-type marks for list rows: image, video, link, text. */
    val Image: ImageVector by lazy {
        outline("Image") {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            moveTo(4f, 17f); lineTo(9f, 12f); lineTo(13f, 16f); lineTo(16f, 13f); lineTo(20f, 17f)
        }
    }

    val PlayCircle: ImageVector by lazy {
        outline("PlayCircle") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(10f, 8.5f); lineTo(15.5f, 12f); lineTo(10f, 15.5f); close()
        }
    }

    val Link: ImageVector by lazy {
        outline("Link") {
            moveTo(10f, 7f); lineTo(7f, 7f); arcTo(5f, 5f, 0f, false, false, 7f, 17f); lineTo(10f, 17f)
            moveTo(14f, 7f); lineTo(17f, 7f); arcTo(5f, 5f, 0f, false, true, 17f, 17f); lineTo(14f, 17f)
            moveTo(8f, 12f); lineTo(16f, 12f)
        }
    }

    val Text: ImageVector by lazy {
        outline("Text") {
            moveTo(5f, 6f); lineTo(19f, 6f)
            moveTo(5f, 10f); lineTo(19f, 10f)
            moveTo(5f, 14f); lineTo(19f, 14f)
            moveTo(5f, 18f); lineTo(13f, 18f)
        }
    }

    val RemoveCircle: ImageVector by lazy {
        outline("RemoveCircle") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(8f, 12f); lineTo(16f, 12f)
        }
    }

    val PersonAdd: ImageVector by lazy {
        outline("PersonAdd") {
            moveTo(10f, 4f); arcTo(3.5f, 3.5f, 0f, true, true, 10f, 11f); arcTo(3.5f, 3.5f, 0f, true, true, 10f, 4f); close()
            moveTo(3f, 20f); curveTo(3f, 16f, 6f, 14f, 10f, 14f); curveTo(14f, 14f, 17f, 16f, 17f, 20f); close()
            moveTo(19f, 7f); lineTo(19f, 13f)
            moveTo(16f, 10f); lineTo(22f, 10f)
        }
    }

    private fun PathBuilder.bookmark() {
        moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f); lineTo(17.5f, 20.5f); lineTo(12f, 16.5f)
        lineTo(6.5f, 20.5f); close()
    }

    private fun outline(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun filled(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()
}
