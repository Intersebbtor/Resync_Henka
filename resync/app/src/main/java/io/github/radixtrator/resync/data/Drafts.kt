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

/**
 * Unsent composer text. [key] names what it was written for ("reply:t1_abc", "edit:t3_xyz",
 * "message:spez", "submit"); [label] says so in the drafts list. [savedAt] is in epoch seconds.
 */
@Serializable
data class Draft(val key: String, val text: String, val label: String = "", val savedAt: Long = 0)

/** Sync's composer drafts: saved as you type, restored when the same composer opens, gone once sent. Newest first. */
class DraftStore(
    private val prefs: SharedPreferences,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Draft.serializer())
    private val _drafts = MutableStateFlow(load())
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()

    fun get(key: String): Draft? = _drafts.value.firstOrNull { it.key == key }

    /** Saves [text] under [key], moving it to the top; blank text removes the draft. */
    fun save(key: String, text: String, label: String = "") {
        if (text.isBlank()) return clear(key)
        val current = get(key)
        if (current != null && current.text == text && current.label == label) return
        change { list -> (listOf(Draft(key, text, label, now())) + list.filter { it.key != key }).take(MAX) }
    }

    fun clear(key: String) {
        if (get(key) == null) return
        change { list -> list.filter { it.key != key } }
    }

    private fun change(transform: (List<Draft>) -> List<Draft>) {
        _drafts.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(serializer, _drafts.value)) }
    }

    private fun load(): List<Draft> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "drafts.v1"
        const val MAX = 50
    }
}
