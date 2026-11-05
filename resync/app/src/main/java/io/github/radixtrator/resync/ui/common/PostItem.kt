@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute

/** Things a list can do to a post; implemented by [ListingViewModel]. */
class PostCallbacks(
    val onVote: (Post, Int) -> Unit,
    val onToggleSave: (Post) -> Unit,
    val onHide: ((Post) -> Unit)?,
    val onDelete: (Post) -> Unit,
)

fun ListingViewModel<*>.postCallbacks(allowHide: Boolean = true) = PostCallbacks(
    onVote = ::vote,
    onToggleSave = ::toggleSave,
    onHide = if (allowHide) ::hide else null,
    onDelete = ::delete,
)

/** Applies the NSFW "hide" setting to a list of posts. */
@Composable
fun <T> List<T>.visiblePosts(postOf: (T) -> Post?): List<T> {
    val hideNsfw = LocalSettings.current.nsfw == NsfwMode.HIDE
    return remember(this, hideNsfw) { if (hideNsfw) filterNot { postOf(it)?.over18 == true } else this }
}

/** A post in whichever layout the user picked. */
@Composable
fun PostListItem(post: Post, showSubreddit: Boolean, callbacks: PostCallbacks) {
    val nav = LocalNavigator.current
    var sheetOpen by remember { mutableStateOf(false) }
    val open = { nav.push(PostRoute(post.id)) }
    when (LocalSettings.current.layout) {
        PostLayout.CARD -> PostCard(post, showSubreddit, open, callbacks) { sheetOpen = true }
        PostLayout.COMPACT -> CompactPostRow(post, showSubreddit, open, callbacks) { sheetOpen = true }
    }
    if (sheetOpen) {
        PostActionsSheet(
            post = post,
            onDismiss = { sheetOpen = false },
            onHide = callbacks.onHide?.let { hide -> { hide(post) } },
            onDelete = { callbacks.onDelete(post) },
        )
    }
}

@Composable
private fun PostCard(post: Post, showSubreddit: Boolean, onOpen: () -> Unit, callbacks: PostCallbacks, onMore: () -> Unit) {
    val nav = LocalNavigator.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onMore)) {
            MediaPreview(post, onClick = { nav.openMedia(post) })
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaLine(post, showSubreddit = showSubreddit, onSubreddit = { nav.push(FeedRoute(post.subreddit)) })
                Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (post.isSelf && post.selfText.isNotBlank()) {
                    Text(
                        post.selfText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.media is PostMedia.Link) LinkRow(post, onClick = { nav.openMedia(post) })
            }
            VoteBar(
                likes = post.likes,
                score = post.score,
                saved = post.saved,
                commentCount = post.numComments,
                onVote = { callbacks.onVote(post, it) },
                onToggleSave = { callbacks.onToggleSave(post) },
                onComments = onOpen,
                onMore = onMore,
            )
        }
    }
}

@Composable
private fun CompactPostRow(post: Post, showSubreddit: Boolean, onOpen: () -> Unit, callbacks: PostCallbacks, onMore: () -> Unit) {
    val nav = LocalNavigator.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onMore)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaLine(post, showSubreddit = showSubreddit, onSubreddit = { nav.push(FeedRoute(post.subreddit)) })
                Text(
                    post.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!post.isSelf) {
                Spacer(Modifier.width(12.dp))
                PostThumbnail(post, onClick = { nav.openMedia(post) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            VoteButtons(post.likes, post.score, { callbacks.onVote(post, it) }, compact = true)
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.Comment, null, tint = muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(Format.count(post.numComments), style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.weight(1f))
            if (post.saved) Icon(AppIcons.BookmarkFilled, "Saved", tint = io.github.radixtrator.resync.ui.theme.ResyncColors.Saved, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun PostActionsSheet(
    post: Post,
    onDismiss: () -> Unit,
    onHide: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val isOwn = LocalAccount.current?.equals(post.author, ignoreCase = true) == true
    val url = "https://www.reddit.com${post.permalink}"
    var confirmDelete by remember { mutableStateOf(false) }

    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetItem("r/${post.subreddit}", AppIcons.Sort) { act { nav.push(FeedRoute(post.subreddit)) } }
            SheetItem("u/${post.author}", Icons.Default.Person) { act { nav.push(UserRoute(post.author)) } }
            SheetItem("Share", Icons.Default.Share) { act { nav.share(url, post.title) } }
            SheetItem("Copy link", AppIcons.Comment) { act { clipboard.setText(AnnotatedString(url)) } }
            SheetItem("Open in browser", AppIcons.OpenExternal) { act { nav.openExternal(url) } }
            onHide?.let { hide -> SheetItem("Hide", AppIcons.Bookmark) { act(hide) } }
            if (isOwn && post.isSelf) {
                SheetItem("Edit", Icons.Default.Edit) {
                    act { nav.push(ComposeRoute(ComposeMode.EDIT, target = post.fullname, quote = post.title, initial = post.selfText)) }
                }
            }
            if (isOwn) SheetItem("Delete", Icons.Default.Delete) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "post",
            onConfirm = {
                confirmDelete = false
                act(onDelete)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SheetItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}
