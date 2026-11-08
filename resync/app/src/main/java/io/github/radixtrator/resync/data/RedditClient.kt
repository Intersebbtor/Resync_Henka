package io.github.radixtrator.resync.data

import android.content.res.AssetManager
import android.util.Log
import io.github.radixtrator.resync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class RedditException(message: String, val code: Int = 0) : IOException(message)

/**
 * OAuth + HTTP for Reddit's API.
 *
 * Logged in: authorization_code -> refresh_token -> short-lived bearer tokens.
 * Logged out: app-only tokens from the "installed_client" grant. Both go to oauth.reddit.com.
 */
class RedditClient(
    private val configStore: ApiConfigStore,
    private val tokens: AccountStore,
    private val assets: AssetManager,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply { if (BuildConfig.DEBUG) addInterceptor(::logExchange) }
        .build()
    private val tokenLock = Mutex()

    private val config: ApiConfig
        get() = configStore.load() ?: throw RedditException("Reddit API isn't set up yet")

    val redirectUri: String get() = config.redirectUri

    fun authorizeUrl(state: String): String =
        "https://www.reddit.com/api/v1/authorize.compact".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", config.redirectUri)
            .addQueryParameter("duration", "permanent")
            .addQueryParameter("scope", SCOPES)
            .build()
            .toString()

    /** Exchanges the code from the login redirect for tokens, saves the account and makes it active. */
    suspend fun completeLogin(code: String): String {
        val response = tokenRequest(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
        )
        val refresh = response.string("refresh_token")
            ?: throw RedditException("Reddit didn't return a refresh token")
        val access = storeAccessToken(response)
        // Ask who this is with the new token directly; the active account isn't switched yet.
        val me = withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$API_BASE/api/v1/me")
                .header("User-Agent", config.userAgent)
                .header("Authorization", "bearer $access")
                .build()
            http.newCall(request).execute().use { it.body.string() }
        }
        val name = runCatching { JSON.parseToJsonElement(me).jsonObject.string("name") }.getOrNull()
        if (name == null) {
            tokens.clearSession()
            throw RedditException("Couldn't read the account name")
        }
        tokens.upsert(Account(name, refresh))
        tokens.activeUsername = name
        return name
    }

    suspend fun get(path: String, params: Map<String, String?> = emptyMap()): JsonElement {
        if (configStore.demoMode) return demoResponse(path, isNextPage = params["after"] != null)
        val url = "$API_BASE$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("raw_json", "1")
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        return execute(Request.Builder().url(url).get())
    }

    suspend fun post(path: String, form: Map<String, String>): JsonElement {
        if (configStore.demoMode) return DemoWrites.respond(path, form)
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url("$API_BASE$path").post(body))
    }

    /**
     * Uploads an image to Reddit's media storage (lease from /api/media/asset.json, then a multipart POST to the
     * returned S3 form) and returns the hosted URL a "kind=image" submission takes.
     */
    suspend fun uploadImage(filename: String, mime: String, bytes: ByteArray): String {
        val lease = post("/api/media/asset.json", mapOf("filepath" to filename, "mimetype" to mime)).jsonObject
        val args = lease.obj("args") ?: throw RedditException("Reddit didn't offer an upload")
        val action = args.string("action") ?: throw RedditException("Reddit didn't offer an upload")
        val uploadUrl = if (action.startsWith("//")) "https:$action" else action
        val fields = args.arr("fields").orEmpty().mapNotNull { it as? JsonObject }
        val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).apply {
            fields.forEach { f -> addFormDataPart(f.string("name").orEmpty(), f.string("value").orEmpty()) }
            // S3 wants the file last.
            addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
        }.build()
        val key = fields.firstOrNull { it.string("name") == "key" }?.string("value") ?: throw RedditException("Reddit's upload form had no key")
        val code = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(uploadUrl).post(form).build()).execute().use { it.code }
        }
        if (code !in 200..299) throw RedditException("Image upload failed ($code)", code)
        return uploadUrl.trimEnd('/') + "/" + key
    }

    suspend fun put(path: String, form: Map<String, String>): JsonElement {
        if (configStore.demoMode) return JsonObject(emptyMap())
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url("$API_BASE$path").put(body))
    }

    suspend fun delete(path: String): JsonElement {
        if (configStore.demoMode) return JsonObject(emptyMap())
        return execute(Request.Builder().url("$API_BASE$path").delete())
    }

    private suspend fun execute(builder: Request.Builder): JsonElement {
        var token = accessToken(forceRefresh = false)
        repeat(2) { attempt ->
            val request = builder
                .header("User-Agent", config.userAgent)
                .header("Authorization", "bearer $token")
                .build()
            val (code, body) = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { it.code to it.body.string() }
            }
            if (code == 401 && attempt == 0) {
                token = accessToken(forceRefresh = true)
                return@repeat
            }
            if (code !in 200..299) throw RedditException(httpError(code), code)
            return if (body.isBlank()) JsonObject(emptyMap()) else JSON.parseToJsonElement(body)
        }
        throw RedditException(httpError(401), 401)
    }

    private suspend fun accessToken(forceRefresh: Boolean): String = tokenLock.withLock {
        val cached = tokens.accessToken
        if (!forceRefresh && cached != null && System.currentTimeMillis() < tokens.accessExpiresAt - 60_000) {
            return cached
        }
        val refresh = tokens.active?.refreshToken
        val response = if (refresh != null) {
            tokenRequest("grant_type" to "refresh_token", "refresh_token" to refresh)
        } else {
            tokenRequest("grant_type" to INSTALLED_CLIENT_GRANT, "device_id" to tokens.deviceId)
        }
        storeAccessToken(response)
    }

    private fun storeAccessToken(response: JsonObject): String {
        val token = response.string("access_token") ?: throw RedditException("Reddit didn't return an access token")
        val expiresIn = response.long("expires_in") ?: 3600
        tokens.accessToken = token
        tokens.accessExpiresAt = System.currentTimeMillis() + expiresIn * 1000
        return token
    }

    private suspend fun tokenRequest(vararg fields: Pair<String, String>): JsonObject {
        val cfg = config
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("User-Agent", cfg.userAgent)
            // Installed apps have no secret: "client_id:".
            .header("Authorization", Credentials.basic(cfg.clientId, ""))
            .post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
            .build()
        val (code, body) = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { it.code to it.body.string() }
        }
        val json = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull()
        json?.string("error")?.let { throw RedditException(tokenError(it), code) }
        if (code !in 200..299 || json == null) throw RedditException(tokenError("http $code"), code)
        return json
    }

    private suspend fun demoResponse(path: String, isNextPage: Boolean): JsonElement = withContext(Dispatchers.IO) {
        if (isNextPage) return@withContext JSON.parseToJsonElement("""{"kind":"Listing","data":{"after":null,"children":[]}}""")
        val file = when {
            path.startsWith("/comments/") -> "demo/comments.json"
            path.startsWith("/api/v1/me") -> "demo/me.json"
            path.startsWith("/subreddits/mine") -> "demo/subreddits.json"
            path.startsWith("/subreddits/search") -> "demo/subreddit_search.json"
            path.startsWith("/api/morechildren") -> "demo/morechildren.json"
            path.matches(Regex("/r/[^/]+/about")) -> "demo/subreddit_about.json"
            path.matches(Regex("/user/[^/]+/about")) -> "demo/user_about.json"
            path.startsWith("/user/") -> "demo/user_overview.json"
            path.startsWith("/message/") -> "demo/inbox.json"
            else -> "demo/listing.json"
        }
        val text = runCatching { assets.open(file).bufferedReader().use { it.readText() } }
            .getOrElse { throw RedditException("Demo data is only bundled in debug builds") }
        JSON.parseToJsonElement(text)
    }

    /** Debug builds log each exchange and the start of any error body; headers (and so tokens) never. */
    private fun logExchange(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val line = "${request.method} ${request.url.encodedPath} -> ${response.code}"
        if (response.isSuccessful) Log.d(TAG, line) else Log.w(TAG, "$line ${response.peekBody(500).string()}")
        return response
    }

    private fun httpError(code: Int) = when (code) {
        401 -> "Reddit rejected the login (401). Try logging in again."
        403 -> "Reddit refused the request (403). It may be private, quarantined, or blocked for this client ID."
        404 -> "Not found (404)."
        429 -> "Rate limited by Reddit (429). Slow down for a minute."
        in 500..599 -> "Reddit is having trouble ($code)."
        else -> "Request failed ($code)."
    }

    private fun tokenError(error: String) = when (error) {
        "invalid_grant" -> "Login expired or was revoked. Log in again."
        "unauthorized_client", "invalid_client", "http 401" ->
            "Reddit rejected the client ID. Check it in Settings."
        else -> "Couldn't get a Reddit token ($error)."
    }

    companion object {
        private const val TAG = "ResyncHttp"
        private const val API_BASE = "https://oauth.reddit.com"
        private const val INSTALLED_CLIENT_GRANT = "https://oauth.reddit.com/grants/installed_client"
        private const val SCOPES =
            "account,identity,edit,flair,history,mysubreddits,privatemessages,read,report,save,submit,subscribe,vote,wikiread,modposts,modflair,modmail"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
