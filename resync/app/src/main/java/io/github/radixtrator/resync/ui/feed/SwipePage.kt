package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.CARD_BLUR_WIDTH
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.PostTitle
import io.github.radixtrator.resync.ui.common.rememberPreviewRequest
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.dataSavingActive
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.common.shortDomain
import io.github.radixtrator.resync.ui.common.shouldBlur
import io.github.radixtrator.resync.ui.home.FeedIcon
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.theme.ResyncColors

// Sync's Swipe mode page (sync-reference/SYNC_PARITY_LOG.md §3.20): a header over media that fills the rest of
// the page, with the post's actions in the screen's bottom bar ([SwipeBottomBar]) rather than on the page.

/**
 * One Swipe mode page: sub icon, `r/sub` (accent), author • age • domain • flair (accent), points • comments and
 * the title, then the media centered in the remaining space. Tapping the header opens the post; tapping the
 * media opens it in the viewer ([onMedia]); text posts show their selftext, scrollable, instead.
 */
@Composable
internal fun SwipePage(post: Post, iconUrl: String?, onOpen: () -> Unit, onMedia: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SwipeHeader(post, iconUrl, Modifier.combinedClickable(onClick = onOpen, onLongClick = onMore))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (post.media == PostMedia.None) SwipeSelfText(post) else SwipeMedia(post, onMedia)
        }
    }
}

@Composable
private fun SwipeHeader(post: Post, iconUrl: String?, modifier: Modifier) {
    val settings = LocalSettings.current
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val meta = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedIcon(post.subreddit, iconUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append("r/")
                        withStyle(SpanStyle(color = accent)) { append(post.subreddit) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildAnnotatedString {
                        val parts = buildList {
                            if (settings.showPostAuthor) add(post.author)
                            add(Format.timeAgo(post.createdUtc))
                            shortDomain(post)?.let { add(it) }
                        }
                        append(parts.joinToString(" • "))
                        post.flair?.takeIf { settings.showPostFlair }?.let {
                            append(" • ")
                            withStyle(SpanStyle(color = accent)) { append(it) }
                        }
                        if (post.over18) withStyle(SpanStyle(color = ResyncColors.Nsfw, fontWeight = FontWeight.Bold)) { append(" • NSFW") }
                        if (post.spoiler) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" • Spoiler") }
                    },
                    style = meta,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildAnnotatedString {
                        val pointsColor = when (post.likes) {
                            true -> ResyncColors.Upvote
                            false -> ResyncColors.Downvote
                            null -> muted
                        }
                        withStyle(SpanStyle(color = pointsColor)) { append(pointsLabel(post.score, settings.shortScores)) }
                        append(" • ")
                        append(commentsLabel(post.numComments, settings.shortScores))
                    },
                    style = meta,
                    color = muted,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PostTitle(post, MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp), maxLines = 6)
    }
}

/** The selftext in full, scrollable within the page; links and spoilers in it stay tappable. */
@Composable
private fun SwipeSelfText(post: Post) {
    if (post.selfText.isBlank()) return
    val text = post.selfTextHtml?.let { rememberRedditHtml(it) } ?: AnnotatedString(post.selfText)
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RedditBodyText(
            text,
            MaterialTheme.typography.bodyLarge,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        )
    }
}

/**
 * The post's media fit to the page and centered: the image itself, a video's or gallery's preview, or a link's
 * preview, with a badge in the middle for galleries, videos and links. Blurred NSFW and spoiler posts follow the
 * feed's rules ([shouldBlur]); data saving leaves the image unloaded, as in feeds.
 */
@Composable
private fun SwipeMedia(post: Post, onMedia: () -> Unit) {
    val blur = post.shouldBlur()
    val saving = dataSavingActive()
    val image = post.media.swipeImage()?.takeUnless { saving }
    Box(Modifier.fillMaxSize().clickable(onClick = onMedia), contentAlignment = Alignment.Center) {
        if (image != null) {
            AsyncImage(
                model = rememberPreviewRequest(image.url, blurWidth = if (blur) CARD_BLUR_WIDTH else null),
                contentDescription = post.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val badge: Pair<ImageVector, String>? = when (post.media) {
            is PostMedia.Gallery -> AppIcons.Gallery to "Gallery"
            is PostMedia.Video -> AppIcons.PlayCircle to "Play video"
            is PostMedia.Link -> AppIcons.OpenExternal to "Open link"
            // A plain image needs no badge unless its picture is missing (data saving).
            is PostMedia.Image -> if (image == null) AppIcons.Image to "Image" else null
            PostMedia.None -> null
        }
        if (blur) {
            Text(
                if (post.over18) "NSFW" else "Spoiler",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (post.over18) ResyncColors.Nsfw else Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else if (badge != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                SwipeBadge(badge.first, badge.second)
                // A link without a preview would be an empty page, so it names where it goes.
                if (post.media is PostMedia.Link && image == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        post.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        }
    }
}

/** The page's image: a still image itself, else the preview Reddit gave (links only when it's a real preview). */
private fun PostMedia.swipeImage(): ImageSource? = when (this) {
    is PostMedia.Image -> if (isGif) preview ?: ImageSource(url, 0, 0) else ImageSource(url, preview?.width ?: 0, preview?.height ?: 0)
    is PostMedia.Gallery -> preview ?: images.firstOrNull()
    is PostMedia.Video -> preview
    is PostMedia.Link -> thumbnail?.takeIf { it.width == 0 || it.width >= 320 }
    PostMedia.None -> null
}

/** Sync's round type badge in the middle of the media. */
@Composable
private fun SwipeBadge(icon: ImageVector, description: String) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(28.dp)) }
    }
}

/**
 * Sync's Swipe mode bottom bar for the post on screen: ▲, ▼, save, comments (opens the post), download (images,
 * galleries and video; greyed out otherwise) and ⋮ (post options).
 */
@Composable
internal fun SwipeBottomBar(
    post: Post,
    canDownload: Boolean,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onComments: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onVote(if (post.likes == true) 0 else 1) }) {
                Icon(AppIcons.ArrowUp, "Upvote", tint = if (post.likes == true) ResyncColors.Upvote else muted)
            }
            IconButton(onClick = { onVote(if (post.likes == false) 0 else -1) }) {
                Icon(AppIcons.ArrowDown, "Downvote", tint = if (post.likes == false) ResyncColors.Downvote else muted)
            }
            IconButton(onClick = onToggleSave) {
                Icon(
                    if (post.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    if (post.saved) "Unsave" else "Save",
                    tint = if (post.saved) ResyncColors.Saved else muted,
                )
            }
            IconButton(onClick = onComments) { Icon(AppIcons.Comment, "Comments", tint = muted) }
            IconButton(onClick = onDownload, enabled = canDownload) {
                Icon(AppIcons.Download, "Download", tint = if (canDownload) muted else muted.copy(alpha = 0.38f))
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Post options", tint = muted) }
        }
    }
}

private fun pointsLabel(score: Int, short: Boolean) = if (score == 1) "1 point" else "${Format.count(score, short)} points"
private fun commentsLabel(n: Int, short: Boolean) = if (n == 1) "1 comment" else "${Format.count(n, short)} comments"
