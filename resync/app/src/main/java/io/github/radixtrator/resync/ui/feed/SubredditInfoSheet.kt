@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.rememberRedditHtml

/** The About sheet's ⋮ menu: Sync's, less Edit flair and Add to multi (Resync has no flair editor or multis). */
enum class AboutAction(val label: String, val icon: ImageVector) {
    WIKI("View wiki", AppIcons.Book),
    FLAIRS("Filter by flair", AppIcons.Tag),
    RULES("View rules", AppIcons.Text),
    SHARE("Share", Icons.Outlined.Share),
    MESSAGE_MODS("Message mods", Icons.Outlined.Email),
    MODS("View mods", Icons.Outlined.Person),
}

/**
 * Sync's About sheet: the banner with the icon across its lower edge, the name and member count, a ⋮ menu,
 * then Join / Watch / Favorite chips and the sidebar in a card.
 */
@Composable
fun SubredditInfoSheet(
    about: Subreddit?,
    watching: Boolean,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onToggleWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAction: (AboutAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        if (about == null) {
            SheetHandle()
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        // The banner, with the icon hanging 24dp below it.
        Box(Modifier.fillMaxWidth().height(BANNER + 24.dp)) {
            Box(Modifier.fillMaxWidth().height(BANNER).background(MaterialTheme.colorScheme.surfaceVariant)) {
                about.bannerUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
            Box(Modifier.align(Alignment.TopCenter)) { SheetHandle() }
            // Sync centers the icon a little above the banner's lower edge.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = BANNER - 32.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(about.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                about.iconUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
            // Sync's ⋮ sits at the right, just under the banner.
            AboutMenu(onAction, Modifier.align(Alignment.BottomEnd).offset(y = 20.dp))
        }
        Text(
            about.name,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = TextUnit.Unspecified),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 56.dp, end = 56.dp),
        )
        // No online count: Reddit reports 0 for every community now.
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(Format.members(about.subscribers), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                val chip = MaterialTheme.colorScheme.surfaceVariant
                val joined = about.isSubscribed
                HeaderChip(if (joined) "Joined" else "Join", if (joined) Icons.Outlined.Check else AppIcons.PlaylistAdd, onToggleSubscribe, container = chip)
                HeaderChip(if (watching) "Watching" else "Watch", if (watching) Icons.Filled.Notifications else Icons.Outlined.Notifications, onToggleWatch, container = chip)
                HeaderChip("Favorite", if (favorite) AppIcons.BookmarkFilled else AppIcons.Bookmark, onToggleFavorite, container = chip)
            }
            about.descriptionHtml?.takeIf { it.isNotBlank() }?.let { html ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    RedditBodyText(
                        rememberRedditHtml(html),
                        MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified),
                        Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private val BANNER = 80.dp

@Composable
private fun AboutMenu(onAction: (AboutAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AboutAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, null) },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
        }
    }
}
