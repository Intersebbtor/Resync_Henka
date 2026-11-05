package io.github.radixtrator.resync.ui.media

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import io.github.radixtrator.resync.ui.common.DismissibleMedia
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings

@OptIn(UnstableApi::class)
private val SoftwareFirst = MediaCodecSelector { mimeType, secure, tunneling ->
    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling).sortedBy { it.hardwareAccelerated }
}

/**
 * Full-screen Media3 player for v.redd.it (HLS) and mp4 clips. [loop] is for GIF-style clips:
 * they repeat silently and hide the controller until tapped.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(url: String, loop: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val mute = LocalSettings.current.muteVideos || loop
    var error by remember { mutableStateOf<String?>(null) }
    // Hardware decoders occasionally die mid-stream (Media3's own fallback only covers ones that
    // fail to start), so a decoding failure gets one retry with software decoders first.
    var softwareFirst by remember { mutableStateOf(false) }
    val player = remember(url, softwareFirst) {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .apply { if (softwareFirst) setMediaCodecSelector(SoftwareFirst) }
        ExoPlayer.Builder(context, renderers).build().apply {
            val item = MediaItem.Builder()
                .setUri(url)
                .apply { if (url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            setMediaItem(item)
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (mute) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED && !softwareFirst) {
                    softwareFirst = true
                } else {
                    error = "Can't play this video (${e.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    DismissibleMedia(onDismiss = onClose) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        controllerAutoShow = !loop
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = {
                    it.player = player // replaced when retrying with software decoders
                    // The controller's buttons would sit on top of the error message.
                    it.useController = error == null
                },
                modifier = Modifier.fillMaxSize(),
            )
            error?.let { message ->
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(message, color = Color.White, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            error = null
                            player.prepare()
                            player.play()
                        }) { Text("Retry") }
                        OutlinedButton(onClick = { nav.openExternal(url) }) { Text("Open in browser", color = Color.White) }
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}
