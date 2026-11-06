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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.rememberRedditHtml

/** Community header + sidebar, with join/leave. */
@Composable
fun SubredditInfoSheet(
    about: Subreddit?,
    onDismiss: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (about == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ModalBottomSheet
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            about.bannerUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
                        about.iconUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("r/${about.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (about.title.isNotBlank()) Text(about.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // No online count: Reddit reports 0 for every community now.
                Text(
                    "${Format.members(about.subscribers)} members · created ${Format.timeAgo(about.createdUtc)} ago",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (about.isSubscribed) {
                        OutlinedButton(onClick = onToggleSubscribe) { Text("Leave") }
                    } else {
                        Button(onClick = onToggleSubscribe) { Text("Join") }
                    }
                    OutlinedButton(onClick = onSubmit) { Text("Create post") }
                }
                if (about.publicDescription.isNotBlank()) Text(about.publicDescription)
                about.descriptionHtml?.let { html ->
                    HorizontalDivider()
                    RedditBodyText(rememberRedditHtml(html), MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
