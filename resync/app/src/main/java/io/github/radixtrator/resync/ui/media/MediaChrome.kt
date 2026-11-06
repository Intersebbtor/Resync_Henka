package io.github.radixtrator.resync.ui.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.radixtrator.resync.data.MediaDownloads
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.PostRoute

/** Sync's media info sheet: a translucent layer over the media, with a denser bar of actions at the bottom. */
internal val MediaPanelColor = Color(0xFF1C1E22).copy(alpha = 0.78f)
private val MediaBarColor = Color(0xFF2E3237).copy(alpha = 0.9f)

/** 🔍 then the media's host and URL, then [details] ("Image resolution: …"), as in Sync's sheet. */
@Composable
internal fun MediaLinkInfo(url: String, details: List<String>) {
    val host = url.substringAfter("://").substringBefore('/')
    // Sync indents the text to 62dp, past the 🔍.
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            (listOf(host, url) + details).forEach { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Comments (when the media came from a post), Open in browser, Copy link and Share. */
@Composable
internal fun MediaActionBar(url: String, postId: String?) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().background(MediaBarColor).navigationBarsPadding().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (postId != null) {
            IconButton(onClick = { nav.push(PostRoute(postId)) }) { Icon(AppIcons.Comment, "Comments", tint = Color.White) }
        }
        IconButton(onClick = { nav.openExternal(url) }) { Icon(AppIcons.OpenExternal, "Open in browser", tint = Color.White) }
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(url))
            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
        }) { Icon(AppIcons.Copy, "Copy link", tint = Color.White) }
        IconButton(onClick = { nav.share(url, "") }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
    }
}

/** Starts a download, first asking for storage access on Android 9 and older (newer versions need none). */
@Composable
internal fun rememberDownloader(downloads: MediaDownloads): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { url ->
            if (granted) downloads.save(url) else Toast.makeText(context, "Saving needs storage access", Toast.LENGTH_SHORT).show()
        }
        pending = null
    }
    return { url ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = url
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            downloads.save(url)
        }
    }
}
