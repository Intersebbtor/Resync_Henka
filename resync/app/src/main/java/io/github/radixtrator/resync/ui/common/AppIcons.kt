package io.github.radixtrator.resync.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
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

    /** Sync's solid thumbtack, after pinned titles and in pinned comments' headers. */
    val Pin: ImageVector by lazy {
        solid("Pin") {
            moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 5f); lineTo(14.5f, 5f); lineTo(14.5f, 10f); lineTo(17.5f, 13.5f)
            lineTo(17.5f, 15f); lineTo(12.75f, 15f); lineTo(12.75f, 21f); lineTo(11.25f, 21f); lineTo(11.25f, 15f)
            lineTo(6.5f, 15f); lineTo(6.5f, 13.5f); lineTo(9.5f, 10f); lineTo(9.5f, 5f); lineTo(8f, 5f); close()
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

    /** Straight vote arrows, as on Sync's comment action bar (chevrons there mean "go to parent"). */
    val ArrowUp: ImageVector by lazy {
        outline("ArrowUp") {
            moveTo(12f, 20f); lineTo(12f, 4f)
            moveTo(5f, 11f); lineTo(12f, 4f); lineTo(19f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        outline("ArrowDown") {
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(5f, 13f); lineTo(12f, 20f); lineTo(19f, 13f)
        }
    }

    /** Sync's moderator mark: a shield with its left half filled. */
    val Shield: ImageVector by lazy {
        ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f)
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
                curveTo(19f, 15.5f, 16f, 19f, 12f, 21f); curveTo(8f, 19f, 5f, 15.5f, 5f, 11f); lineTo(5f, 6f); close()
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f); lineTo(5f, 6f); lineTo(5f, 11f); curveTo(5f, 15.5f, 8f, 19f, 12f, 21f); close()
            }
            .build()
    }

    /** Sync's OP mark: a solid person speaking. */
    val Speaker: ImageVector by lazy {
        ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 4f); arcTo(4f, 4f, 0f, true, true, 9f, 12f); arcTo(4f, 4f, 0f, true, true, 9f, 4f); close()
                moveTo(1.5f, 21f); curveTo(1.5f, 16.5f, 5f, 14f, 9f, 14f); curveTo(13f, 14f, 16.5f, 16.5f, 16.5f, 21f); close()
            }
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(16.5f, 5.5f); curveTo(18f, 7f, 18f, 10f, 16.5f, 11.5f)
                moveTo(19.5f, 3f); curveTo(22.5f, 6f, 22.5f, 11f, 19.5f, 14f)
            }
            .build()
    }

    val Clock: ImageVector by lazy {
        outline("Clock") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    val CollapseAll: ImageVector by lazy {
        outline("CollapseAll") {
            moveTo(7f, 4f); lineTo(12f, 9f); lineTo(17f, 4f)
            moveTo(7f, 20f); lineTo(12f, 15f); lineTo(17f, 20f)
        }
    }

    val Copy: ImageVector by lazy {
        outline("Copy") {
            moveTo(9f, 8f); lineTo(19f, 8f); lineTo(19f, 20f); lineTo(9f, 20f); close()
            moveTo(15f, 8f); lineTo(15f, 4f); lineTo(5f, 4f); lineTo(5f, 16f); lineTo(9f, 16f)
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

    // Comment and post options: Report, Tag user, Filter.
    val Filter: ImageVector by lazy {
        outline("Filter") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(7f, 12f); lineTo(17f, 12f)
            moveTo(10f, 17f); lineTo(14f, 17f)
        }
    }

    val Report: ImageVector by lazy {
        outline("Report") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(12f, 7.5f); lineTo(12f, 13f)
            moveTo(12f, 16f); lineTo(12f, 16.5f)
        }
    }

    val TagUser: ImageVector by lazy {
        outline("TagUser") {
            moveTo(5f, 20f); lineTo(12f, 4f); lineTo(19f, 20f)
            moveTo(8f, 14f); lineTo(16f, 14f)
        }
    }

    // Profile chips: Trophies, Block.
    val Trophy: ImageVector by lazy {
        outline("Trophy") {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f); curveTo(17f, 12f, 15f, 14f, 12f, 14f); curveTo(9f, 14f, 7f, 12f, 7f, 9f); close()
            moveTo(17f, 6f); lineTo(20f, 6f); curveTo(20f, 9f, 18.5f, 10.5f, 17f, 10.5f)
            moveTo(7f, 6f); lineTo(4f, 6f); curveTo(4f, 9f, 5.5f, 10.5f, 7f, 10.5f)
            moveTo(12f, 14f); lineTo(12f, 18f)
            moveTo(8f, 20f); lineTo(16f, 20f)
        }
    }

    val Block: ImageVector by lazy {
        outline("Block") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(5.6f, 5.6f); lineTo(18.4f, 18.4f)
        }
    }

    // Community header chips: Join, Wiki, Flairs, Random.
    val PlaylistAdd: ImageVector by lazy {
        outline("PlaylistAdd") {
            moveTo(3f, 6f); lineTo(15f, 6f)
            moveTo(3f, 10.5f); lineTo(15f, 10.5f)
            moveTo(3f, 15f); lineTo(11f, 15f)
            moveTo(18f, 11.5f); lineTo(18f, 19.5f)
            moveTo(14f, 15.5f); lineTo(22f, 15.5f)
        }
    }

    val Book: ImageVector by lazy {
        outline("Book") {
            moveTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 20.5f); lineTo(6f, 20.5f); close()
            moveTo(10f, 3.5f); lineTo(10f, 10.5f); lineTo(12f, 9f); lineTo(14f, 10.5f); lineTo(14f, 3.5f)
        }
    }

    val Tag: ImageVector by lazy {
        outline("Tag") {
            moveTo(3.5f, 7f); lineTo(15f, 7f); lineTo(20f, 12f); lineTo(15f, 17f); lineTo(3.5f, 17f); close()
        }
    }

    val Dice: ImageVector by lazy {
        outline("Dice") {
            moveTo(10f, 7f); lineTo(10f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 14f); lineTo(17f, 14f)
            moveTo(3.5f, 10f); lineTo(14f, 10f); lineTo(14f, 20.5f); lineTo(3.5f, 20.5f); close()
            moveTo(6.5f, 13f); lineTo(7f, 13f)
            moveTo(10.5f, 17.5f); lineTo(11f, 17.5f)
            moveTo(16.5f, 6.5f); lineTo(17f, 6.5f)
        }
    }

    // Media viewer controls (Sync's video bar and info sheet).
    val Pause: ImageVector by lazy {
        filled("Pause") {
            moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
            moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
        }
    }

    val Restart: ImageVector by lazy {
        filled("Restart") {
            moveTo(6f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(6f, 18f); close()
            moveTo(18f, 6f); lineTo(18f, 18f); lineTo(9.5f, 12f); close()
        }
    }

    val VolumeOn: ImageVector by lazy {
        filled("VolumeOn") {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4f); lineTo(12f, 20f); lineTo(7f, 15f); lineTo(3f, 15f); close()
            moveTo(14f, 7.5f); curveTo(17.5f, 9f, 17.5f, 15f, 14f, 16.5f); lineTo(14f, 7.5f); close()
        }
    }

    val VolumeOff: ImageVector by lazy {
        filled("VolumeOff") {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4f); lineTo(12f, 20f); lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
    }

    /** Sync's "slower" speed button; a filled circle with the minus cut out. */
    val RemoveCircleFilled: ImageVector by lazy {
        ImageVector.Builder("RemoveCircleFilled", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM17,13L7,13v-2h10v2z"), fill = SolidColor(Color.Black))
            .build()
    }

    val Download: ImageVector by lazy {
        filled("Download") {
            moveTo(10f, 3f); lineTo(14f, 3f); lineTo(14f, 9f); lineTo(18f, 9f); lineTo(12f, 15.5f); lineTo(6f, 9f); lineTo(10f, 9f); close()
            moveTo(5f, 18f); lineTo(19f, 18f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        }
    }

    // Settings categories without a material-icons-core glyph.
    val Tablet: ImageVector by lazy {
        outline("Tablet") {
            moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close()
            moveTo(10f, 18f); lineTo(14f, 18f)
        }
    }

    val Palette: ImageVector by lazy {
        outline("Palette") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, false, 12f, 21f)
            curveTo(13.4f, 21f, 14f, 20.1f, 14f, 19.2f); curveTo(14f, 18f, 13f, 17.7f, 13f, 16.5f)
            curveTo(13f, 15.5f, 13.8f, 15f, 14.8f, 15f); lineTo(17f, 15f)
            curveTo(19.2f, 15f, 21f, 13.2f, 21f, 11f); curveTo(21f, 6.5f, 17f, 3f, 12f, 3f); close()
            dot(7.5f, 12f); dot(9f, 8f); dot(13.5f, 7f); dot(17f, 9.5f)
        }
    }

    /** Up and down arrows: data use. */
    val DataTransfer: ImageVector by lazy {
        outline("DataTransfer") {
            moveTo(8f, 20f); lineTo(8f, 5f)
            moveTo(4f, 9f); lineTo(8f, 5f); lineTo(12f, 9f)
            moveTo(16f, 4f); lineTo(16f, 19f)
            moveTo(12f, 15f); lineTo(16f, 19f); lineTo(20f, 15f)
        }
    }

    /** A cloud with an up arrow: backup. */
    val Backup: ImageVector by lazy {
        outline("Backup") {
            moveTo(7f, 18f); arcTo(4.5f, 4.5f, 0f, false, true, 6.5f, 9f)
            arcTo(6f, 6f, 0f, false, true, 18f, 10.5f)
            arcTo(3.75f, 3.75f, 0f, false, true, 17.5f, 18f); close()
            moveTo(12f, 16f); lineTo(12f, 11f)
            moveTo(9.5f, 13.5f); lineTo(12f, 11f); lineTo(14.5f, 13.5f)
        }
    }

    /** A dot drawn by the outline's stroke: a circle too small to show a hole. */
    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x - 0.4f, y); arcTo(0.4f, 0.4f, 0f, true, true, x + 0.4f, y); arcTo(0.4f, 0.4f, 0f, true, true, x - 0.4f, y); close()
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

    private fun solid(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black), pathBuilder = block).build()

    private fun filled(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()
}
