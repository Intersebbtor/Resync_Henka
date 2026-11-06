package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Inbox items already announced, so an unread message gets one notification rather than one per check.
 * Keeps the most recent [MAX].
 */
class NotifiedMessages(private val prefs: SharedPreferences) {
    private val notified = LinkedHashSet(prefs.getString(KEY, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty())

    @Synchronized
    fun unseen(items: List<InboxItem>): List<InboxItem> = items.filter { it.fullname !in notified }

    @Synchronized
    fun remember(fullnames: Collection<String>) {
        notified.addAll(fullnames)
        while (notified.size > MAX) notified.remove(notified.first())
        prefs.edit { putString(KEY, notified.joinToString(",")) }
    }

    private companion object {
        const val KEY = "notified.v1"
        const val MAX = 500
    }
}
