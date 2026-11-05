package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Where the user left a comment page: list position and which threads they folded. */
@Serializable
data class PostMemory(val index: Int = 0, val offset: Int = 0, val collapsed: Set<String> = emptySet())

/**
 * Sync's "Remember comment position": reopening a post returns to the same spot with the same threads
 * folded. Keyed by post (and focused comment, for single-thread pages); keeps the [MAX] most recent.
 */
class PostMemoryStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PostMemory.serializer())
    private val entries: LinkedHashMap<String, PostMemory> = load()

    operator fun get(key: String): PostMemory? = entries[key]

    fun saveScroll(key: String, index: Int, offset: Int) = update(key) { it.copy(index = index, offset = offset) }

    fun saveCollapsed(key: String, collapsed: Set<String>) = update(key) { it.copy(collapsed = collapsed) }

    private fun update(key: String, change: (PostMemory) -> PostMemory) {
        val next = change(entries.remove(key) ?: PostMemory())
        entries[key] = next // re-inserted, so it's the most recent
        while (entries.size > MAX) entries.remove(entries.keys.first())
        prefs.edit { putString(KEY, json.encodeToString(serializer, entries)) }
    }

    private fun load(): LinkedHashMap<String, PostMemory> = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        ?.let { LinkedHashMap(it) }
        ?: LinkedHashMap()

    companion object {
        private const val KEY = "post_memory.v1"
        private const val MAX = 200

        fun key(postId: String, focusCommentId: String?) = focusCommentId?.let { "$postId/$it" } ?: postId
    }
}
