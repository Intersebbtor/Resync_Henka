@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Add
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
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    ADD_TO_MULTI("Add to multi", Icons.Default.Add),
    EDIT_FLAIR("Edit flair", AppIcons.Tag),
    SHARE("Share", Icons.Outlined.Share),
    MESSAGE_MODS("Message mods", Icons.Outlined.Email),
    MODS("View mods", Icons.Outlined.Person),
    MOD_TOOLS("Mod tools", AppIcons.Shield),
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
        // No online count: Reddit reports 0 for every community now.
        AboutHeader(about.bannerUrl, about.iconUrl, about.name, menu = { AboutMenu(about.name, onAction, it) }) {
            AboutStat(Icons.Outlined.Person, Format.members(about.subscribers))
        }
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

/**
 * The top of Sync's About sheets (communities and users): the banner with the icon across its lower edge,
 * the name, a row of [stats], and an optional ⋮ [menu] at the right, then a divider.
 */
@Composable
fun AboutHeader(
    bannerUrl: String?,
    iconUrl: String?,
    name: String,
    menu: (@Composable (Modifier) -> Unit)? = null,
    stats: @Composable RowScope.() -> Unit,
) {
    // The banner, with the icon hanging 24dp below it.
    Box(Modifier.fillMaxWidth().height(BANNER + 24.dp)) {
        Box(Modifier.fillMaxWidth().height(BANNER).background(MaterialTheme.colorScheme.surfaceVariant)) {
            bannerUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
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
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            iconUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        // Sync's ⋮ sits at the right, just under the banner.
        menu?.invoke(Modifier.align(Alignment.BottomEnd).offset(y = 20.dp))
    }
    Text(
        name,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = TextUnit.Unspecified),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 56.dp, end = 56.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = stats,
    )
    HorizontalDivider()
}

/** One of [AboutHeader]'s stats: a small icon and a number. */
@Composable
fun AboutStat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutMenu(community: String, onAction: (AboutAction) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Mod tools only for communities you moderate.
            val moderated = LocalContainer.current.moderated.collectAsStateWithLifecycle().value
            AboutAction.entries.filter { it != AboutAction.MOD_TOOLS || moderated.any { m -> m.equals(community, ignoreCase = true) } }.forEach { action ->
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
