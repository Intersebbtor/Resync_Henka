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

    private val intMapSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val _colors = MutableStateFlow(
        prefs.getString(COLORS_KEY, null)?.let { runCatching { json.decodeFromString(intMapSerializer, it) }.getOrNull() }.orEmpty(),
    )

    /** Optional ARGB tint per tagged user (lowercase name); users without one use the theme's default tag color. */
    val colors: StateFlow<Map<String, Int>> = _colors.asStateFlow()

    fun colorOf(user: String): Int? = _colors.value[user.lowercase()]

    /** A null [argb] goes back to the default tint. */
    fun setColor(user: String, argb: Int?) {
        _colors.update { if (argb == null) it - user.lowercase() else it + (user.lowercase() to argb) }
        prefs.edit { putString(COLORS_KEY, json.encodeToString(intMapSerializer, _colors.value)) }
    }

    fun tagOf(user: String): String? = _tags.value[user.lowercase()]

    /** A blank [tag] removes it. */
    fun set(user: String, tag: String?) {
        val text = tag?.trim()?.takeIf { it.isNotEmpty() }
        _tags.update { if (text == null) it - user.lowercase() else it + (user.lowercase() to text) }
        if (text == null) setColor(user, null)
        prefs.edit { putString(KEY, json.encodeToString(serializer, _tags.value)) }
    }

    private fun load(): Map<String, String> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "usertags.v1"
        const val COLORS_KEY = "usertagcolors.v1"
    }
}
