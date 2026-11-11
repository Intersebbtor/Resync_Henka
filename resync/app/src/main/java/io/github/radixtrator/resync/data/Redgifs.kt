package io.github.radixtrator.resync.data

import io.github.radixtrator.resync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.io.IOException

/** A RedGifs clip's files: [sd] (RedGifs' "-mobile" encode) and [hd], which can be the same file. */
data class RedgifsVideo(val sd: String, val hd: String, val hasAudio: Boolean)

/**
 * RedGifs links (redgifs.com/watch/<id>) are web pages, not video files, so they're played by asking RedGifs' v2
 * API for the clip's mp4s. The API wants a bearer token, which anyone can get from /v2/auth/temporary; it's tied
 * to the User-Agent (and address) that asked for it, so every RedGifs request sends [USER_AGENT].
 */
object Redgifs {
    /** Sent on the token, API, playback and download requests alike; the same one [MediaDownloads] sends. */
    val USER_AGENT = "Resync/${BuildConfig.VERSION_NAME} (Android)"

    private val WATCH = Regex(
        """^https?://(?:www\.|v3\.)?redgifs\.com/(?:watch|ifr)/([a-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private const val API = "https://api.redgifs.com/v2"
    /** Temporary tokens last about a day; renew well before that rather than wait for a 401. */
    private const val TOKEN_TTL_MS = 12 * 60 * 60 * 1000L

    private val http = Http.base.newBuilder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var token: String? = null
    private var tokenTime = 0L
    private val cache = HashMap<String, RedgifsVideo>()

    /** The clip id in a RedGifs watch or embed link, lowercased as the API wants it; null for other links. */
    fun id(url: String): String? = WATCH.find(url)?.groupValues?.get(1)?.lowercase()

    /** The clip's mp4s, from RedGifs' API (cached for the session). Throws when RedGifs can't be reached or has no such clip. */
    suspend fun resolve(id: String): RedgifsVideo = withContext(Dispatchers.IO) {
        lock.withLock { cache[id] } ?: fetchGif(id, renewToken = false).also { video -> lock.withLock { cache[id] = video } }
    }

    private suspend fun fetchGif(id: String, renewToken: Boolean): RedgifsVideo {
        val auth = token(renew = renewToken)
        val request = Request.Builder().url("$API/gifs/$id").header("Authorization", "Bearer $auth").build()
        val (code, body) = http.newCall(request).execute().use { it.code to it.body.string() }
        // An expired or refused token: get a fresh one and try once more.
        if ((code == 401 || code == 403) && !renewToken) return fetchGif(id, renewToken = true)
        if (code == 404 || code == 410) throw IOException("This RedGifs clip was removed")
        if (code !in 200..299) throw IOException("RedGifs HTTP $code")
        return parseGif(json.parseToJsonElement(body) as? JsonObject) ?: throw IOException("RedGifs sent no video")
    }

    private suspend fun token(renew: Boolean): String {
        lock.withLock {
            val now = System.currentTimeMillis()
            token?.takeIf { !renew && now - tokenTime < TOKEN_TTL_MS }?.let { return it }
            val fresh = http.newCall(Request.Builder().url("$API/auth/temporary").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("RedGifs sign-in HTTP ${response.code}")
                (json.parseToJsonElement(response.body.string()) as? JsonObject)?.string("token")
            } ?: throw IOException("RedGifs sent no token")
            token = fresh
            tokenTime = now
            return fresh
        }
    }

    /** The files in a /v2/gifs/<id> response: {"gif": {"urls": {"sd", "hd", ...}, "hasAudio"}}. */
    internal fun parseGif(root: JsonObject?): RedgifsVideo? {
        val gif = root?.obj("gif") ?: return null
        val urls = gif.obj("urls") ?: return null
        val hd = urls.string("hd")
        val sd = urls.string("sd")
        if (hd == null && sd == null) return null
        return RedgifsVideo(sd = sd ?: hd!!, hd = hd ?: sd!!, hasAudio = gif.bool("hasAudio"))
    }
}
