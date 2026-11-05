package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.theme.ResyncColors

private fun PostMedia.largePreview(): ImageSource? = when (this) {
    is PostMedia.Image -> preview ?: ImageSource(url, 0, 0)
    is PostMedia.Gallery -> preview
    is PostMedia.Video -> preview
    is PostMedia.Link, PostMedia.None -> null
}

fun Post.thumbnail(): ImageSource? = when (val m = media) {
    is PostMedia.Link -> m.thumbnail
    else -> m.largePreview()
}

/** Whether this post's preview should be blurred under the current settings. */
@Composable
fun Post.shouldBlur(): Boolean {
    val settings = LocalSettings.current
    return (over18 && settings.nsfw == NsfwMode.BLUR) || (spoiler && settings.blurSpoilers)
}

/** Full-width media for image, gallery and video posts. Links use [LinkRow] instead. */
@Composable
fun MediaPreview(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxAspect: Float = 2f,
    minAspect: Float = 0.8f,
) {
    val preview = post.media.largePreview() ?: return
    val blur = post.shouldBlur()
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(preview.aspectRatio.coerceIn(minAspect, maxAspect))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = preview.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .then(if (blur) Modifier.blur(40.dp) else Modifier),
        )
        MediaBadges(post, Modifier.matchParentSize(), playSize = 56.dp)
        if (blur) {
            Badge(
                if (post.over18) "NSFW" else "Spoiler",
                Modifier.align(Alignment.BottomStart),
                color = if (post.over18) ResyncColors.Nsfw else Color.Black.copy(alpha = 0.6f),
            )
        }
    }
}

/** Gallery count, GIF tag, or a play button, overlaid on a preview. */
@Composable
fun MediaBadges(post: Post, modifier: Modifier = Modifier, playSize: Dp = 32.dp, small: Boolean = false) {
    Box(modifier) {
        when (val m = post.media) {
            is PostMedia.Gallery -> Badge(if (small) "${m.images.size}" else "1 / ${m.images.size}", Modifier.align(Alignment.TopEnd), small = small)
            is PostMedia.Image -> if (m.isGif) Badge("GIF", Modifier.align(Alignment.TopEnd), small = small)
            is PostMedia.Video -> Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.Center).size(playSize),
            ) {
                Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.padding(playSize / 5))
            }
            else -> Unit
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.6f), small: Boolean = false) {
    Text(
        text,
        color = Color.White,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        modifier = modifier
            .padding(if (small) 4.dp else 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = if (small) 5.dp else 8.dp, vertical = if (small) 1.dp else 3.dp),
    )
}

/** Square thumbnail for the compact layout; falls back to a link glyph. */
@Composable
fun PostThumbnail(post: Post, onClick: () -> Unit, size: Dp = 72.dp) {
    val thumb = post.thumbnail()
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().then(if (post.shouldBlur()) Modifier.blur(16.dp) else Modifier),
            )
            MediaBadges(post, Modifier.matchParentSize(), playSize = 28.dp, small = true)
        } else {
            Icon(AppIcons.OpenExternal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LinkRow(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val link = post.media as? PostMedia.Link ?: return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (link.thumbnail != null) {
            AsyncImage(
                model = link.thumbnail.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(post.domain, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                link.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(AppIcons.OpenExternal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** "r/sub · u/author · 5h · domain" plus flags. */
@Composable
fun MetaLine(post: Post, showSubreddit: Boolean, onSubreddit: (() -> Unit)?, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        if (post.stickied) {
            withStyle(SpanStyle(color = ResyncColors.Moderator, fontWeight = FontWeight.SemiBold)) { append("Pinned · ") }
        }
        if (showSubreddit) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("r/${post.subreddit}") }
            append(" · ")
        }
        append("u/${post.author} · ${Format.timeAgo(post.createdUtc)}")
        if (!post.isSelf && post.domain.isNotEmpty() && !post.domain.startsWith("self.")) append(" · ${post.domain}")
        if (post.over18) withStyle(SpanStyle(color = ResyncColors.Nsfw, fontWeight = FontWeight.Bold)) { append(" · NSFW") }
        if (post.spoiler) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" · Spoiler") }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onSubreddit != null && showSubreddit) Modifier.clickable(onClick = onSubreddit) else Modifier,
        )
        post.flair?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Upvote · score · downvote ... comments · save · more. */
@Composable
fun VoteBar(
    likes: Boolean?,
    score: Int,
    saved: Boolean,
    commentCount: Int?,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onComments: (() -> Unit)?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VoteButtons(likes, score, onVote)
        Spacer(Modifier.weight(1f))
        if (commentCount != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (onComments != null) Modifier.clickable(onClick = onComments) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Comment, "Comments", tint = muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(Format.count(commentCount), style = MaterialTheme.typography.labelLarge, color = muted)
            }
        }
        IconButton(onClick = onToggleSave) {
            Icon(
                if (saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                if (saved) "Unsave" else "Save",
                tint = if (saved) ResyncColors.Saved else muted,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, "More", tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun VoteButtons(likes: Boolean?, score: Int, onVote: (Int) -> Unit, compact: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scoreColor = when (likes) {
        true -> ResyncColors.Upvote
        false -> ResyncColors.Downvote
        null -> if (compact) muted else MaterialTheme.colorScheme.onSurface
    }
    val iconSize = if (compact) 20.dp else 24.dp
    val buttonSize = if (compact) 32.dp else 48.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onVote(if (likes == true) 0 else 1) }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.KeyboardArrowUp, "Upvote", tint = if (likes == true) ResyncColors.Upvote else muted, modifier = Modifier.size(iconSize))
        }
        Text(
            Format.count(score),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = scoreColor,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { onVote(if (likes == false) 0 else -1) }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.KeyboardArrowDown, "Downvote", tint = if (likes == false) ResyncColors.Downvote else muted, modifier = Modifier.size(iconSize))
        }
    }
}
