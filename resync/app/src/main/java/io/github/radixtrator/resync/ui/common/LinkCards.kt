package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val IMAGE_LINK = Regex("""\.(?:jpe?g|png|gif|webp)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
private val IMAGE_HOSTS = setOf("i.redd.it", "preview.redd.it", "i.imgur.com")

/** Whether a footnoted link can show itself as its own thumbnail. */
fun isImageLink(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore('/').lowercase()
    return host in IMAGE_HOSTS || IMAGE_LINK.containsMatchIn(url.substringBefore('#'))
}

/**
 * Sync's link cards under a self-post or comment: one per footnoted link, "(n) url" beside a
 * thumbnail (the image itself for image links, a link glyph otherwise). Tapping opens the link.
 */
@Composable
fun LinkCards(links: List<String>, modifier: Modifier = Modifier) {
    if (links.isEmpty()) return
    val uri = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEachIndexed { i, url ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { uri.openUri(url) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isImageLink(url)) {
                        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
                Text(
                    "(${i + 1}) $url",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
            }
        }
    }
}
