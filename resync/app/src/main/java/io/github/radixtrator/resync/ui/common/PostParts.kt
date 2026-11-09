package io.github.radixtrator.resync.ui.common

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.radixtrator.resync.data.DataSaving
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.navigation.LocalSettings

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

/**
 * Whether Sync's "Data saving mode" applies right now: feeds then leave thumbnails and card previews unloaded,
 * and Reddit videos open in SD. "On mobile data" means any metered connection, checked once per item.
 */
@Composable
fun dataSavingActive(): Boolean {
    val context = LocalContext.current
    return when (LocalSettings.current.dataSaving) {
        DataSaving.NEVER -> false
        DataSaving.ALWAYS -> true
        DataSaving.MOBILE -> remember(context) { context.onMeteredNetwork() }
    }
}

/** True on a metered connection (mobile data, a metered Wi-Fi), as Android reports it. */
fun Context.onMeteredNetwork(): Boolean =
    getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

/** Whether this post's preview should be blurred under the current settings. */
@Composable
fun Post.shouldBlur(): Boolean = shouldBlur(LocalSettings.current)

fun Post.shouldBlur(settings: Settings): Boolean =
    (over18 && settings.nsfw == NsfwMode.BLUR) || (spoiler && settings.blurSpoilers)

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
