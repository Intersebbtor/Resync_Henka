package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A watched community and the creation time (epoch seconds) of the newest post already notified about or skipped. */
@Serializable
data class WatchedCommunity(val name: String, val newestSeen: Long)

/**
 * Communities with Sync's Watch on. The background check notifies about posts newer than [WatchedCommunity.newestSeen],
 * so watching starts from the moment the chip is tapped. Keyed by lowercase name.
 */
class WatchStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _watched = MutableStateFlow(load())
    val watched: StateFlow<Map<String, WatchedCommunity>> = _watched.asStateFlow()

    fun isWatching(name: String) = name.lowercase() in _watched.value

    fun watch(name: String, since: Long) = change { it + (name.lowercase() to WatchedCommunity(name, since)) }

    fun unwatch(name: String) = change { it - name.lowercase() }

    fun markSeen(name: String, newest: Long) = change { all ->
        val key = name.lowercase()
        val current = all[key] ?: return@change all
        all + (key to current.copy(newestSeen = maxOf(current.newestSeen, newest)))
    }

    private fun change(transform: (Map<String, WatchedCommunity>) -> Map<String, WatchedCommunity>) {
        _watched.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(ListSerializer(WatchedCommunity.serializer()), _watched.value.values.toList())) }
    }

    private fun load(): Map<String, WatchedCommunity> =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(WatchedCommunity.serializer()), it) }.getOrNull() }
            ?.associateBy { it.name.lowercase() }
            .orEmpty()

    private companion object {
        const val KEY = "watched.v1"
    }
}
