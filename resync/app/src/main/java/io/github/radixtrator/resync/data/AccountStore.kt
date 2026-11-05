package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom

@Serializable
data class Account(val username: String, val refreshToken: String)

/**
 * Saved accounts (each a refresh token) plus the access token for whichever is active.
 * No active account means browsing logged out with app-only tokens.
 */
class AccountStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Account.serializer())

    init {
        migrateSingleAccount()
    }

    var accounts: List<Account>
        get() = prefs.getString(KEY_ACCOUNTS, null)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            .orEmpty()
        private set(value) = prefs.edit { putString(KEY_ACCOUNTS, json.encodeToString(listSerializer, value)) }

    var activeUsername: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) = prefs.edit { putString(KEY_ACTIVE, value) }

    val active: Account? get() = activeUsername?.let { name -> accounts.firstOrNull { it.username == name } }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit { putString(KEY_ACCESS, value) }

    var accessExpiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES, 0)
        set(value) = prefs.edit { putLong(KEY_EXPIRES, value) }

    /** Reddit wants a stable 20-30 character ID for app-only ("installed_client") tokens. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: buildString {
            val random = SecureRandom()
            repeat(25) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }.also { prefs.edit { putString(KEY_DEVICE, it) } }

    fun upsert(account: Account) {
        accounts = accounts.filterNot { it.username.equals(account.username, ignoreCase = true) } + account
    }

    fun remove(username: String) {
        accounts = accounts.filterNot { it.username == username }
        if (activeUsername == username) activeUsername = null
    }

    fun removeAll() {
        accounts = emptyList()
        activeUsername = null
        clearSession()
    }

    /** Drops the cached access token, e.g. after switching accounts. */
    fun clearSession() = prefs.edit {
        remove(KEY_ACCESS)
        remove(KEY_EXPIRES)
    }

    // Milestone 1 stored a single account under these keys.
    private fun migrateSingleAccount() {
        val refresh = prefs.getString("auth.refresh_token", null)
        val name = prefs.getString("auth.username", null)
        if (refresh != null && name != null && accounts.none { it.username == name }) {
            upsert(Account(name, refresh))
            activeUsername = name
        }
        prefs.edit {
            remove("auth.refresh_token")
            remove("auth.username")
        }
    }

    private companion object {
        const val KEY_ACCOUNTS = "auth.accounts"
        const val KEY_ACTIVE = "auth.active"
        const val KEY_ACCESS = "auth.access_token"
        const val KEY_EXPIRES = "auth.access_expires_at"
        const val KEY_DEVICE = "auth.device_id"
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    }
}
