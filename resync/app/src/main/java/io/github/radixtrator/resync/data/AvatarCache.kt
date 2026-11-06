package io.github.radixtrator.resync.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * Comment authors' profile pictures (Sync's "Show profile pictures"), by account fullname, fetched in batches
 * as comments load and kept for the session. A null entry means asked already, with no picture to show.
 */
class AvatarCache(private val api: RedditApi) {
    private val _urls = MutableStateFlow<Map<String, String?>>(emptyMap())
    val urls: StateFlow<Map<String, String?>> = _urls.asStateFlow()

    suspend fun load(accountIds: Collection<String>) {
        val wanted = accountIds.filter { it.startsWith("t2_") && it !in _urls.value }.distinct()
        if (wanted.isEmpty()) return
        // Claimed up front, so pages loading at the same time don't ask for the same accounts twice.
        _urls.update { it + wanted.associateWith { null } }
        val found = try {
            api.avatars(wanted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ResyncAvatars", "Couldn't load profile pictures", e)
            emptyMap()
        }
        _urls.update { it + found }
    }
}
