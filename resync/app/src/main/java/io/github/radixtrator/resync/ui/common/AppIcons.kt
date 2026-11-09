package io.github.radixtrator.resync.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's icon choices, all from Material Icons, named for what they mean here so each screen picks by role.
 */
object AppIcons {
    val Comment: ImageVector get() = Icons.Outlined.ChatBubbleOutline
    val Bookmark: ImageVector get() = Icons.Outlined.BookmarkBorder
    val BookmarkFilled: ImageVector get() = Icons.Filled.Bookmark
    val Gallery: ImageVector get() = Icons.Outlined.PhotoLibrary
    val OpenExternal: ImageVector get() = Icons.AutoMirrored.Outlined.OpenInNew
    val Reply: ImageVector get() = Icons.AutoMirrored.Outlined.Reply
    val Sort: ImageVector get() = Icons.AutoMirrored.Outlined.Sort

    /** Sort sheet glyphs: Hot, New, Rising, Top, Controversial. */
    val Flame: ImageVector get() = Icons.Outlined.Whatshot
    val Burst: ImageVector get() = Icons.Outlined.NewReleases
    val TrendingUp: ImageVector get() = Icons.AutoMirrored.Outlined.TrendingUp
    val TrendingDown: ImageVector get() = Icons.AutoMirrored.Outlined.TrendingDown
    val Bars: ImageVector get() = Icons.Outlined.BarChart
    val Compass: ImageVector get() = Icons.Outlined.Explore
    val Moon: ImageVector get() = Icons.Outlined.DarkMode

    /** Sync's "Change view". */
    val ViewStack: ImageVector get() = Icons.Outlined.ViewAgenda
    val DoubleCheck: ImageVector get() = Icons.Outlined.DoneAll

    /** Sync's solid thumbtack, after pinned titles and in pinned comments' headers. */
    val Pin: ImageVector get() = Icons.Filled.PushPin

    /** Content-type marks for list rows: image, video, link, text. */
    val Image: ImageVector get() = Icons.Outlined.Image
    val PlayCircle: ImageVector get() = Icons.Outlined.PlayCircle
    val Link: ImageVector get() = Icons.Outlined.Link
    val Text: ImageVector get() = Icons.AutoMirrored.Outlined.Notes

    /** Straight vote arrows, as on Sync's comment action bar (chevrons there mean "go to parent"). */
    val ArrowUp: ImageVector get() = Icons.Outlined.ArrowUpward
    val ArrowDown: ImageVector get() = Icons.Outlined.ArrowDownward

    /** Moderator mark and mod tools. */
    val Shield: ImageVector get() = Icons.Outlined.Shield

    /** Sync's OP mark: a person speaking. */
    val Speaker: ImageVector get() = Icons.Filled.RecordVoiceOver
    val Clock: ImageVector get() = Icons.Outlined.Schedule
    val CollapseAll: ImageVector get() = Icons.Outlined.UnfoldLess
    val Copy: ImageVector get() = Icons.Outlined.ContentCopy
    val RemoveCircle: ImageVector get() = Icons.Outlined.RemoveCircleOutline
    val PersonAdd: ImageVector get() = Icons.Outlined.PersonAdd

    /** Comment and post options: Filter, Report, Tag user. */
    val Filter: ImageVector get() = Icons.Outlined.FilterList
    val Report: ImageVector get() = Icons.Outlined.Report
    val TagUser: ImageVector get() = Icons.Outlined.Badge

    /** Profile chips: Trophies, Block. */
    val Trophy: ImageVector get() = Icons.Outlined.EmojiEvents
    val Block: ImageVector get() = Icons.Outlined.Block

    /** Community header chips: Join, Wiki, Flairs, Random. */
    val PlaylistAdd: ImageVector get() = Icons.AutoMirrored.Outlined.PlaylistAdd
    val Book: ImageVector get() = Icons.Outlined.Book
    val Tag: ImageVector get() = Icons.AutoMirrored.Outlined.Label
    val Dice: ImageVector get() = Icons.Outlined.Casino

    /** Media viewer controls (Sync's video bar and info sheet). */
    val Pause: ImageVector get() = Icons.Filled.Pause
    val Restart: ImageVector get() = Icons.Filled.SkipPrevious
    val VolumeOn: ImageVector get() = Icons.AutoMirrored.Filled.VolumeUp
    val VolumeOff: ImageVector get() = Icons.AutoMirrored.Filled.VolumeOff

    /** Sync's "slower" speed button. */
    val RemoveCircleFilled: ImageVector get() = Icons.Filled.RemoveCircle
    val Download: ImageVector get() = Icons.Filled.Download

    /** Settings categories. */
    val Tablet: ImageVector get() = Icons.Outlined.TabletAndroid
    val Palette: ImageVector get() = Icons.Outlined.Palette
    val DataTransfer: ImageVector get() = Icons.Outlined.DataUsage
    val Backup: ImageVector get() = Icons.Outlined.Backup

    /** Quick actions: Dark overlay, Data saving. */
    val Contrast: ImageVector get() = Icons.Outlined.Contrast
    val DataSaver: ImageVector get() = Icons.Outlined.DataSaverOn
}
