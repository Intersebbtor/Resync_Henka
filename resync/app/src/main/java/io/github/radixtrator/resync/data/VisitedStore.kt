package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Post ids the user has opened, so feeds can dim them as Sync does ("Mark posts as read").
 * Local only; keeps the most recent [MAX].
 */
class VisitedStore(private val prefs: SharedPreferences) {
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun mark(postId: String) {
        if (postId in _ids.value) return
        val next = LinkedHashSet(_ids.value).apply {
            add(postId)
            while (size > MAX) remove(first())
        }
        _ids.value = next
        prefs.edit { putString(KEY, next.joinToString(",")) }
    }

    private fun load(): Set<String> =
        prefs.getString(KEY, null)?.split(',')?.filterTo(LinkedHashSet()) { it.isNotBlank() } ?: emptySet()

    private companion object {
        const val KEY = "visited.v1"
        const val MAX = 2000
    }
}
