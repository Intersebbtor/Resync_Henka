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
