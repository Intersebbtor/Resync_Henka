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

    fun mark(postId: String) = markAll(listOf(postId))

    /** Marks several at once (Mark posts as read on scroll), with one update and one write. */
    fun markAll(postIds: List<String>) {
        val current = _ids.value
        if (postIds.all { it in current }) return
        val next = LinkedHashSet(current).apply {
            addAll(postIds)
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
