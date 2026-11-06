package io.github.radixtrator.resync.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import io.github.radixtrator.resync.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

private val REDDIT_VIDEO = Regex("""^https?://v\.redd\.it/([a-z0-9]+)""", RegexOption.IGNORE_CASE)
private val VIDEO_FILE = Regex("""\.(mp4|webm|mov)(\?|$)""", RegexOption.IGNORE_CASE)

/**
 * Sync's download button: saves images to Pictures/Resync and videos to Movies/Resync. Reddit videos
 * are separate DASH video and audio files, so those are fetched and muxed into one mp4 first.
 * Saving runs in the background (leaving the viewer doesn't stop it) and reports with toasts.
 */
class MediaDownloads(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Resync/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()

    fun save(url: String) {
        toast("Downloading…")
        scope.launch {
            val result = runCatching { saveNow(url) }
            withContext(Dispatchers.Main) {
                toast(result.fold({ "Saved to $it" }, { "Download failed: ${it.message ?: it.javaClass.simpleName}" }))
            }
        }
    }

    /** The file's size from a HEAD request, for the info sheet; null when the host doesn't say. */
    suspend fun contentLength(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { it.header("Content-Length")?.toLongOrNull() }
        }.getOrNull()
    }

    private fun saveNow(url: String): String {
        REDDIT_VIDEO.find(url)?.let { return saveRedditVideo(it.groupValues[1]) }
        return fetch(url).use { response ->
            val type = response.header("Content-Type")?.substringBefore(';')?.trim()
            val video = type?.startsWith("video/") ?: VIDEO_FILE.containsMatchIn(url)
            val mime = type ?: if (video) "video/mp4" else "image/jpeg"
            store(fileName(url, mime), mime, video) { out -> response.body.byteStream().use { it.copyTo(out) } }
        }
    }

    private fun saveRedditVideo(id: String): String {
        val base = "https://v.redd.it/$id/"
        val manifest = fetch(base + "DASHPlaylist.mpd").use { it.body.string() }
        val tracks = dashTracks(manifest) ?: throw IOException("no video in the DASH playlist")
        val parts = File(context.cacheDir, "downloads").apply { mkdirs() }
        val video = File(parts, "$id-video.mp4")
        val audio = tracks.audio?.let { File(parts, "$id-audio.mp4") }
        val merged = File(parts, "$id.mp4")
        try {
            download(base + tracks.video, video)
            if (audio != null) {
                download(base + tracks.audio, audio)
                mux(listOf(video, audio), merged)
            } else {
                video.copyTo(merged, overwrite = true)
            }
            return store("$id.mp4", "video/mp4", video = true) { out -> merged.inputStream().use { it.copyTo(out) } }
        } finally {
            video.delete()
            audio?.delete()
            merged.delete()
        }
    }

    private fun fetch(url: String): Response {
        val response = http.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response
    }

    private fun download(url: String, to: File) {
        fetch(url).use { response -> to.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } } }
    }

    /** Copies each file's first track into one mp4 (here: DASH video plus DASH audio). */
    private fun mux(sources: List<File>, to: File) {
        val muxer = MediaMuxer(to.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = sources.map { MediaExtractor().apply { setDataSource(it.path); selectTrack(0) } }
        try {
            val formats = extractors.map { it.getTrackFormat(0) }
            val tracks = formats.map { muxer.addTrack(it) }
            muxer.start()
            val capacity = formats.maxOf { if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
            val buffer = ByteBuffer.allocate(maxOf(capacity, 2 shl 20))
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { i, extractor ->
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val keyFrame = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(tracks[i], buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }
    }

    /** Writes a new shared file and returns the folder it landed in, for the toast. */
    private fun store(name: String, mime: String, video: Boolean, write: (OutputStream) -> Unit): String {
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relative = "$folder/Resync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("couldn't create the file")
            try {
                resolver.openOutputStream(uri)?.use(write) ?: throw IOException("couldn't open the file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // API 26–28: the caller holds WRITE_EXTERNAL_STORAGE.
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(folder), "Resync").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use(write)
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
        }
        return relative
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** A v.redd.it DASH manifest's best video file and, if it has sound, its best audio file (names relative to the post's folder). */
data class DashTracks(val video: String, val audio: String?)

private val ADAPTATION_SET = Regex("""<AdaptationSet\b(.*?)</AdaptationSet>""", RegexOption.DOT_MATCHES_ALL)
private val REPRESENTATION = Regex("""<Representation\b([^>]*)>(.*?)</Representation>""", RegexOption.DOT_MATCHES_ALL)
private val BANDWIDTH = Regex("""bandwidth="(\d+)"""")
private val BASE_URL = Regex("""<BaseURL>\s*([^<]+?)\s*</BaseURL>""")
private val AUDIO_SET = Regex("""(contentType|mimeType)="audio""")

fun dashTracks(manifest: String): DashTracks? {
    var video: Pair<Long, String>? = null
    var audio: Pair<Long, String>? = null
    for (set in ADAPTATION_SET.findAll(manifest)) {
        val isAudio = AUDIO_SET.containsMatchIn(set.value)
        for (rep in REPRESENTATION.findAll(set.groupValues[1])) {
            val file = BASE_URL.find(rep.groupValues[2])?.groupValues?.get(1) ?: continue
            val bandwidth = BANDWIDTH.find(rep.groupValues[1])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (isAudio) {
                if (audio == null || bandwidth > audio.first) audio = bandwidth to file
            } else if (video == null || bandwidth > video.first) {
                video = bandwidth to file
            }
        }
    }
    return video?.let { DashTracks(it.second, audio?.second) }
}

/** The URL's file name, with an extension that matches [mime] when it has none. */
fun fileName(url: String, mime: String): String {
    val last = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/').ifEmpty { "resync" }
    if ('.' in last) return last
    val ext = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> if (mime.startsWith("video/")) "mp4" else "jpg"
    }
    return "$last.$ext"
}
