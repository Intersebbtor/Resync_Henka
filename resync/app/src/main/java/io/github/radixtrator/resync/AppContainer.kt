package io.github.radixtrator.resync

import android.content.Context
import io.github.radixtrator.resync.data.Account
import io.github.radixtrator.resync.data.AccountStore
import io.github.radixtrator.resync.data.ApiConfig
import io.github.radixtrator.resync.data.ApiConfigStore
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.RedditApi
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Things one screen changes that others showing the same content should reflect. */
sealed interface ContentEvent {
    data class Replied(val parentFullname: String, val comment: Comment) : ContentEvent
    data class Edited(val fullname: String, val body: String, val bodyHtml: String?) : ContentEvent
    data class Deleted(val fullname: String) : ContentEvent
}

/** Manual DI: one instance per process, owned by [ResyncApp]. */
class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences("resync", Context.MODE_PRIVATE)
    val configStore = ApiConfigStore(prefs)
    val settings = SettingsStore(prefs)
    private val accountStore = AccountStore(prefs)
    val client = RedditClient(configStore, accountStore, context.assets)
    val api = RedditApi(client)

    private val _username = MutableStateFlow(accountStore.active?.username)
    /** Active account name, or null when browsing logged out. Feeds reload when it changes. */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _accounts = MutableStateFlow(accountStore.accounts.map { it.username })
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _inboxCount = MutableStateFlow(0)
    val inboxCount: StateFlow<Int> = _inboxCount.asStateFlow()

    private val _events = MutableSharedFlow<ContentEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ContentEvent> = _events.asSharedFlow()

    val isConfigured: Boolean get() = configStore.load() != null || configStore.demoMode

    fun emit(event: ContentEvent) {
        _events.tryEmit(event)
    }

    suspend fun login(code: String) {
        val name = client.completeLogin(code)
        _accounts.value = accountStore.accounts.map { it.username }
        _username.value = name
    }

    /** null switches to browsing logged out. */
    fun switchAccount(name: String?) {
        if (name == _username.value) return
        accountStore.activeUsername = name
        accountStore.clearSession()
        _inboxCount.value = 0
        _username.value = name
    }

    /** Forgets the active account and drops to logged out. */
    fun logout() {
        _username.value?.let(accountStore::remove)
        accountStore.clearSession()
        _accounts.value = accountStore.accounts.map { it.username }
        _inboxCount.value = 0
        _username.value = null
    }

    suspend fun refreshInboxCount() {
        if (_username.value == null) return
        runCatching { api.me() }.onSuccess { _inboxCount.value = it.inboxCount }
    }

    fun setInboxCount(count: Int) {
        _inboxCount.value = count
    }

    fun saveConfig(config: ApiConfig) {
        // Tokens are bound to the client ID they were issued for, and demo accounts aren't real.
        if (configStore.demoMode || configStore.load()?.clientId != config.clientId) forgetAllAccounts()
        configStore.demoMode = false
        configStore.save(config)
    }

    fun enableDemo() {
        forgetAllAccounts()
        configStore.demoMode = true
        accountStore.upsert(Account("demo_user", "demo"))
        accountStore.activeUsername = "demo_user"
        _accounts.value = listOf("demo_user")
        _username.value = "demo_user"
    }

    private fun forgetAllAccounts() {
        accountStore.removeAll()
        _accounts.value = emptyList()
        _inboxCount.value = 0
        _username.value = null
    }
}
