package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Sync's "Tag user": your own labels for users, shown next to their name. Local only; keyed by lowercase name. */
class UserTags(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val _tags = MutableStateFlow(load())
    val tags: StateFlow<Map<String, String>> = _tags.asStateFlow()

    fun tagOf(user: String): String? = _tags.value[user.lowercase()]

    /** A blank [tag] removes it. */
    fun set(user: String, tag: String?) {
        val text = tag?.trim()?.takeIf { it.isNotEmpty() }
        _tags.update { if (text == null) it - user.lowercase() else it + (user.lowercase() to text) }
        prefs.edit { putString(KEY, json.encodeToString(serializer, _tags.value)) }
    }

    private fun load(): Map<String, String> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "usertags.v1"
    }
}
