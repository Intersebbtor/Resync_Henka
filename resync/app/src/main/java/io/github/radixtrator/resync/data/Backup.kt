package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
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

/**
 * The signed-in accounts a backup can carry: each account's refresh token, which account was active, and the client
 * ID the tokens were issued to (Reddit only honors a refresh token from the client it was issued for).
 */
@Serializable
data class AccountsBackup(val clientId: String, val accounts: List<Account>, val active: String? = null)

/**
 * A decoded backup file. [prefs] holds only keys Resync backs up, whatever the file contained. [accounts] is the
 * still-encrypted accounts section, if the backup has one; [Backup.decryptAccounts] opens it.
 */
data class BackupDocument(
    val version: Int,
    val created: String?,
    val prefs: Map<String, String>,
    val accounts: JsonObject? = null,
) {
    val hasApiConfig: Boolean get() = Backup.API_CLIENT_ID in prefs
    val apiClientId: String? get() = prefs[Backup.API_CLIENT_ID]
    /** Usernames of the backed-up accounts, stored in the clear so the restore dialog can name them. */
    val accountNames: List<String>
        get() = (accounts?.get("usernames") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
}

/** What [Backup.restore] did, for the dialog after it. */
data class RestoreResult(val signedOut: Boolean, val accountsRestored: Int)

class BackupFormatException(message: String) : Exception(message)

/** The accounts section couldn't be opened: wrong password, or a damaged file. */
class BackupPasswordException : Exception("Wrong password, or the backup is damaged")

/**
 * Sync's Backup now / Restore from backup: the local stores' preference keys as one versioned JSON document,
 * `{"format":"resync-backup","version":1,"created":…,"prefs":{key: value},"accounts":{…}}`. Every backed-up key holds
 * a string (most are a store's own JSON blob), so values are copied as strings.
 *
 * The Reddit API setup (client ID, redirect URI, user agent) isn't secret, so it's always included but only restored
 * on request. Accounts are optional and always encrypted: refresh tokens never leave the device in the clear, so the
 * `accounts` section is AES-256-GCM under a key derived from a password (PBKDF2-SHA256). Access tokens are never
 * included; they expire within the hour anyway. Older Resync versions ignore the `accounts` section.
 *
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

    /** Everything a backup's `prefs` may hold. Anything else in a file is ignored on restore. */
    val ALLOWED_KEYS: Set<String> = (LOCAL_KEYS + API_KEYS).toSet()

    // AccountStore's keys. Tokens only travel in the encrypted accounts section, never in `prefs`.
    private const val AUTH_PREFIX = "auth."
    private const val AUTH_ACCOUNTS = "auth.accounts"
    private const val AUTH_ACTIVE = "auth.active"
    private val SESSION_KEYS = listOf("auth.access_token", "auth.access_expires_at")
    private val SIGN_OUT_KEYS = listOf(AUTH_ACCOUNTS, AUTH_ACTIVE) + SESSION_KEYS

    // The accounts section's cipher. Iterations are stored per file, so raising them later still opens old backups.
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF_ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private val AAD = "$FORMAT/accounts".toByteArray()

    private val json = Json { prettyPrint = true }
    private val compact = Json { ignoreUnknownKeys = true }
    private val accountList = ListSerializer(Account.serializer())
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun isAllowed(key: String): Boolean = key in ALLOWED_KEYS && !key.startsWith(AUTH_PREFIX)

    /** The allowlisted keys from [all] (SharedPreferences.getAll), strings only. */
    fun filter(all: Map<String, *>): Map<String, String> =
        all.entries.mapNotNull { (key, value) -> if (isAllowed(key) && value is String) key to value else null }
            .sortedBy { it.first }
            .toMap()

    /**
     * The backup document for [values]. With [accounts] and a non-empty [password], the accounts go along encrypted;
     * without a password they're left out.
     */
    fun encode(
        values: Map<String, String>,
        createdMillis: Long,
        accounts: AccountsBackup? = null,
        password: CharArray? = null,
    ): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("created", Instant.ofEpochMilli(createdMillis).toString())
            put("prefs", JsonObject(filter(values).mapValues { JsonPrimitive(it.value) }))
            if (accounts != null && accounts.accounts.isNotEmpty() && password != null && password.isNotEmpty()) {
                put("accounts", encryptAccounts(accounts, password))
            }
        },
    )

    /** The signed-in accounts in [prefs], or null when there are none (or only demo mode's). */
    fun accountsIn(prefs: SharedPreferences): AccountsBackup? {
        if (prefs.getBoolean(API_DEMO, false)) return null
        val clientId = prefs.getString(API_CLIENT_ID, null) ?: return null
        val accounts = readAccounts(prefs).takeIf { it.isNotEmpty() } ?: return null
        return AccountsBackup(clientId, accounts, prefs.getString(AUTH_ACTIVE, null))
    }

    /** Backup now: [prefs]' backed-up keys, plus its accounts encrypted with [accountsPassword] if one is given. */
    fun export(
        prefs: SharedPreferences,
        createdMillis: Long = System.currentTimeMillis(),
        accountsPassword: CharArray? = null,
    ): String = encode(filter(prefs.all), createdMillis, accountsPassword?.let { accountsIn(prefs) }, accountsPassword)

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
        // Only a well-formed encrypted section counts; anything else (say, hand-planted plain tokens) is dropped.
        val accounts = root.obj("accounts")?.takeIf { s -> listOf("salt", "iv", "data").all { s.string(it) != null } }
        return BackupDocument(version, root.string("created"), filter(values), accounts)
    }

    /** Opens [doc]'s accounts section with [password]; throws [BackupPasswordException] if it can't. */
    fun decryptAccounts(doc: BackupDocument, password: CharArray): AccountsBackup {
        val section = doc.accounts ?: throw BackupPasswordException()
        return try {
            val iterations = section.long("iterations")?.toInt()?.takeIf { it in 1_000..10_000_000 } ?: KDF_ITERATIONS
            val key = deriveKey(password, unb64.decode(section.string("salt")), iterations)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, unb64.decode(section.string("iv"))))
            cipher.updateAAD(AAD)
            val plain = cipher.doFinal(unb64.decode(section.string("data"))).decodeToString()
            compact.decodeFromString(AccountsBackup.serializer(), plain)
        } catch (e: GeneralSecurityException) {
            throw BackupPasswordException()
        } catch (e: IllegalArgumentException) {
            throw BackupPasswordException()
        }
    }

    /**
     * Writes [doc] into [prefs], synchronously since the app restarts right after.
     *
     * Each of [parts] is replaced: keys the backup lacks go back to their defaults; parts not chosen are left alone.
     * The API setup is only touched when [includeApiConfig] is set (or [accounts] are restored, since their tokens need
     * the client they were issued to) and the backup has one; a different client ID, or leaving demo mode, signs every
     * account out first, as Setup does. [accounts] (from [decryptAccounts]) are then added to whatever accounts remain,
     * replacing same-named ones, and the backed-up active account becomes the active one.
     */
    fun restore(
        prefs: SharedPreferences,
        doc: BackupDocument,
        includeApiConfig: Boolean,
        parts: Set<BackupPart> = BackupPart.entries.toSet(),
        accounts: AccountsBackup? = null,
    ): RestoreResult {
        val values = filter(doc.prefs)
        // What's on the device now, read before any edit is queued.
        val wasDemo = prefs.getBoolean(API_DEMO, false)
        val currentClientId = prefs.getString(API_CLIENT_ID, null)
        val currentAccounts = readAccounts(prefs)
        val currentActive = prefs.getString(AUTH_ACTIVE, null)
        val editor = prefs.edit()
        parts.flatMap { it.keys }.forEach { key ->
            val value = values[key]
            if (value != null) editor.putString(key, value) else editor.remove(key)
        }

        val clientId = values[API_CLIENT_ID]
        val restoreAccounts = accounts?.takeIf { clientId != null && it.clientId == clientId && it.accounts.isNotEmpty() }
        var signedOut = false
        if ((includeApiConfig || restoreAccounts != null) && clientId != null) {
            if (wasDemo || currentClientId != clientId) {
                SIGN_OUT_KEYS.forEach { editor.remove(it) }
                signedOut = currentAccounts.isNotEmpty()
            }
            editor.putBoolean(API_DEMO, false)
            API_KEYS.forEach { key ->
                val value = values[key]
                if (value != null) editor.putString(key, value) else editor.remove(key)
            }
        }

        if (restoreAccounts != null) {
            val kept = if (wasDemo || currentClientId != clientId) emptyList() else currentAccounts
            val incoming = restoreAccounts.accounts.distinctBy { it.username.lowercase() }
            val merged = kept.filterNot { old -> incoming.any { it.username.equals(old.username, ignoreCase = true) } } + incoming
            editor.putString(AUTH_ACCOUNTS, compact.encodeToString(accountList, merged))
            val active = restoreAccounts.active?.takeIf { name -> merged.any { it.username == name } }
                ?: currentActive?.takeIf { name -> merged.any { it.username == name } }
            if (active != null) editor.putString(AUTH_ACTIVE, active) else editor.remove(AUTH_ACTIVE)
            // The cached access token may belong to another account now; a fresh one is fetched on first use.
            SESSION_KEYS.forEach { editor.remove(it) }
        }
        if (!editor.commit()) throw IllegalStateException("couldn't write the restored data")
        return RestoreResult(signedOut, restoreAccounts?.accounts?.size ?: 0)
    }

    /**
     * Everything in [prefs], accounts included, for the automatic copy taken before a restore. It never leaves the
     * app's private storage, so it's no less safe than the preferences file itself.
     */
    fun snapshot(prefs: SharedPreferences, createdMillis: Long = System.currentTimeMillis()): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", "$FORMAT-snapshot")
            put("created", Instant.ofEpochMilli(createdMillis).toString())
            put(
                "prefs",
                JsonObject(
                    prefs.all.entries.sortedBy { it.key }.mapNotNull { (key, value) ->
                        when (value) {
                            is String -> key to JsonPrimitive(value)
                            is Boolean -> key to JsonPrimitive(value)
                            is Long -> key to JsonPrimitive(value)
                            else -> null // Resync stores nothing else
                        }
                    }.toMap(),
                ),
            )
        },
    )

    /** When [snapshot] [text] was taken, or null if it isn't one. */
    fun snapshotCreated(text: String): String? =
        runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?.takeIf { it.string("format") == "$FORMAT-snapshot" }
            ?.let { it.string("created") ?: "" }

    /** Puts [prefs] back exactly as [snapshot] [text] had it. Throws [BackupFormatException] if it isn't one. */
    fun restoreSnapshot(prefs: SharedPreferences, text: String) {
        val root = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?.takeIf { it.string("format") == "$FORMAT-snapshot" }
            ?: throw BackupFormatException("The saved copy is damaged")
        val values = root.obj("prefs") ?: throw BackupFormatException("The saved copy is damaged")
        val editor = prefs.edit().clear()
        values.forEach { (key, value: JsonElement) ->
            val p = value as? JsonPrimitive ?: return@forEach
            when {
                p.isString -> editor.putString(key, p.content)
                p.booleanOrNull != null -> editor.putBoolean(key, p.booleanOrNull!!)
                p.longOrNull != null -> editor.putLong(key, p.longOrNull!!)
            }
        }
        if (!editor.commit()) throw IllegalStateException("couldn't write the saved copy")
    }

    /** The file name Backup now suggests: resync-backup-YYYY-MM-DD.json. */
    fun fileName(date: LocalDate = LocalDate.now()): String = "resync-backup-$date.json"

    private fun readAccounts(prefs: SharedPreferences): List<Account> =
        prefs.getString(AUTH_ACCOUNTS, null)
            ?.let { runCatching { compact.decodeFromString(accountList, it) }.getOrNull() }
            .orEmpty()

    private fun encryptAccounts(accounts: AccountsBackup, password: CharArray): JsonObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, KDF_ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(AAD)
        val data = cipher.doFinal(compact.encodeToString(AccountsBackup.serializer(), accounts).toByteArray())
        return buildJsonObject {
            put("kdf", KDF)
            put("iterations", KDF_ITERATIONS)
            put("cipher", CIPHER)
            put("salt", b64.encodeToString(salt))
            put("iv", b64.encodeToString(iv))
            put("data", b64.encodeToString(data))
            put("usernames", JsonArray(accounts.accounts.map { JsonPrimitive(it.username) }))
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
