package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One group of saved data a backup carries, as the restore dialog names it. The keys copy each store's private KEY
 * constant (the stores all share the "resync" preferences file); the store tests round-trip every store through a
 * backup to keep them in step.
 */
enum class BackupPart(val label: String, val keys: List<String>) {
    SETTINGS("Settings", listOf("settings.v1")),
    FILTERS("Filters", listOf("filters.v1")),
    EXPLORE("Favorites, defaults and recently visited", listOf("explore.v1")),
    USER_TAGS("User tags", listOf("usertags.v1", "usertagcolors.v1")),
    WATCHED("Watched communities and posts", listOf("watched.v1", "watchedposts.v1")),
    READ_POSTS("Read posts", listOf("visited.v1")),
    COMMENT_POSITIONS("Comment positions", listOf("post_memory.v1")),
    RECENTS("Recently viewed posts", listOf("recent_posts.v1")),
    DRAFTS("Drafts", listOf("drafts.v1")),
    NOTIFIED("Messages already notified about", listOf("notified.v1")),
}

/** A decoded backup file. [prefs] holds only keys Resync backs up, whatever the file contained. */
data class BackupDocument(val version: Int, val created: String?, val prefs: Map<String, String>) {
    val hasApiConfig: Boolean get() = Backup.API_CLIENT_ID in prefs
}

class BackupFormatException(message: String) : Exception(message)

/**
 * Sync's Backup now / Restore from backup: the local stores' preference keys as one versioned JSON document,
 * `{"format":"resync-backup","version":1,"created":…,"prefs":{key: value}}`. Every backed-up key holds a string
 * (most are a store's own JSON blob), so values are copied as strings.
 *
 * Accounts are never included: their refresh tokens are secrets, and access tokens expire anyway. The Reddit API
 * setup (client ID, redirect URI, user agent) isn't secret, so it's included but only restored on request.
 * The stores cache in memory, so the app must restart after [restore].
 */
object Backup {
    const val FORMAT = "resync-backup"
    const val VERSION = 1

    // ApiConfigStore's keys.
    const val API_CLIENT_ID = "api.client_id"
    private const val API_DEMO = "api.demo"
    private val API_KEYS = listOf(API_CLIENT_ID, "api.redirect_uri", "api.user_agent")

    /**
     * The local data every version-1 backup carries; a restore resets those it lacks to their defaults.
     * (A store added later needs a version bump, so restoring an older file leaves its data alone.)
     */
    val LOCAL_KEYS: List<String> = BackupPart.entries.flatMap { it.keys }

    /** Everything a backup may hold. Anything else in a file is ignored on restore. */
    val ALLOWED_KEYS: Set<String> = (LOCAL_KEYS + API_KEYS).toSet()

    /** AccountStore's keys (refresh and access tokens, the active account): never exported or imported. */
    private const val AUTH_PREFIX = "auth."
    private val SIGN_OUT_KEYS = listOf("auth.accounts", "auth.active", "auth.access_token", "auth.access_expires_at")

    private val json = Json { prettyPrint = true }

    fun isAllowed(key: String): Boolean = key in ALLOWED_KEYS && !key.startsWith(AUTH_PREFIX)

    /** The allowlisted keys from [all] (SharedPreferences.getAll), strings only. */
    fun filter(all: Map<String, *>): Map<String, String> =
        all.entries.mapNotNull { (key, value) -> if (isAllowed(key) && value is String) key to value else null }
            .sortedBy { it.first }
            .toMap()

    fun encode(values: Map<String, String>, createdMillis: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("created", Instant.ofEpochMilli(createdMillis).toString())
            put("prefs", JsonObject(filter(values).mapValues { JsonPrimitive(it.value) }))
        },
    )

    fun export(prefs: SharedPreferences, createdMillis: Long = System.currentTimeMillis()): String =
        encode(filter(prefs.all), createdMillis)

    /** Parses and checks a backup file; throws [BackupFormatException] with a message for the user. */
    fun decode(text: String): BackupDocument {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw BackupFormatException("That file isn't a Resync backup")
        if (root.string("format") != FORMAT) throw BackupFormatException("That file isn't a Resync backup")
        val version = root.long("version")?.toInt() ?: throw BackupFormatException("That backup has no version")
        if (version < 1) throw BackupFormatException("That backup's version ($version) isn't valid")
        if (version > VERSION) throw BackupFormatException("That backup is from a newer Resync; update the app to restore it")
        val prefs = root.obj("prefs") ?: throw BackupFormatException("That backup has no data")
        val values = prefs.keys.mapNotNull { key -> prefs.string(key)?.let { key to it } }.toMap()
        return BackupDocument(version, root.string("created"), filter(values))
    }

    /**
     * Writes [doc] into [prefs], synchronously since the app restarts right after. Local data is replaced: keys the
     * backup lacks go back to their defaults. The API setup is only touched when [includeApiConfig] is set and the
     * backup has one; a different client ID (or leaving demo mode) signs every account out, as Setup does, since
     * tokens are bound to the client they were issued for. Returns whether accounts were signed out.
     */
    fun restore(prefs: SharedPreferences, doc: BackupDocument, includeApiConfig: Boolean): Boolean {
        val values = filter(doc.prefs)
        val editor = prefs.edit()
        LOCAL_KEYS.forEach { key ->
            val value = values[key]
            if (value != null) editor.putString(key, value) else editor.remove(key)
        }
        var signedOut = false
        val clientId = values[API_CLIENT_ID]
        if (includeApiConfig && clientId != null) {
            if (prefs.getBoolean(API_DEMO, false) || prefs.getString(API_CLIENT_ID, null) != clientId) {
                SIGN_OUT_KEYS.forEach { editor.remove(it) }
                signedOut = true
            }
            editor.putBoolean(API_DEMO, false)
            API_KEYS.forEach { key ->
                val value = values[key]
                if (value != null) editor.putString(key, value) else editor.remove(key)
            }
        }
        editor.commit()
        return signedOut
    }

    /** The file name Backup now suggests: resync-backup-YYYY-MM-DD.json. */
    fun fileName(date: LocalDate = LocalDate.now()): String = "resync-backup-$date.json"
}
