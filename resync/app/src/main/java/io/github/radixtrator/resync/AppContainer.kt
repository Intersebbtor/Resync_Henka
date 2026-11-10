package io.github.radixtrator.resync

import android.content.Context
import android.widget.Toast
import io.github.radixtrator.resync.data.Account
import io.github.radixtrator.resync.data.AccountStore
import io.github.radixtrator.resync.data.ApiConfig
import io.github.radixtrator.resync.data.ApiConfigStore
import io.github.radixtrator.resync.data.AvatarCache
import io.github.radixtrator.resync.data.Backup
import io.github.radixtrator.resync.data.AccountsBackup
import io.github.radixtrator.resync.data.BackupDocument
import io.github.radixtrator.resync.data.BackupPart
import io.github.radixtrator.resync.data.RestoreResult
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.DraftStore
import io.github.radixtrator.resync.data.ExploreStore
import io.github.radixtrator.resync.data.FilterStore
import io.github.radixtrator.resync.data.MediaDownloads
import io.github.radixtrator.resync.data.NotifiedMessages
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.data.RecentPostsStore
import io.github.radixtrator.resync.data.RedditApi
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.ReportReason
import io.github.radixtrator.resync.data.SettingsStore
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.data.UserTags
import io.github.radixtrator.resync.data.VisitedStore
import io.github.radixtrator.resync.data.WatchStore
import io.github.radixtrator.resync.watch.WatchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val visited = VisitedStore(prefs)
    // Stores no screen needs for the first frame load (and parse) when first used, not at launch.
    val postMemory by lazy { PostMemoryStore(prefs) }
    val explore = ExploreStore(prefs)
    val downloads by lazy { MediaDownloads(appContext) }
    val watch = WatchStore(prefs)
    val notifiedMessages by lazy { NotifiedMessages(prefs) }
    val tags = UserTags(prefs)
    val filters = FilterStore(prefs)
    val recentPosts by lazy { RecentPostsStore(prefs) }
    val drafts by lazy { DraftStore(prefs) }
    val readAloud by lazy { ReadAloud(appContext) }
    private val appContext = context.applicationContext
    private val accountStore = AccountStore(prefs)
    val client = RedditClient(configStore, accountStore, context.assets)
    val api = RedditApi(client)
    val avatars by lazy { AvatarCache(api) }

    private val _username = MutableStateFlow(accountStore.active?.username)
    /** Active account name, or null when browsing logged out. Feeds reload when it changes. */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _accounts = MutableStateFlow(accountStore.accounts.map { it.username })
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _inboxCount = MutableStateFlow(0)
    val inboxCount: StateFlow<Int> = _inboxCount.asStateFlow()

    private val _avatar = MutableStateFlow<String?>(null)
    /** The active account's avatar URL, for the toolbar's account button. */
    val avatar: StateFlow<String?> = _avatar.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<String>>(emptyList())
    /** The active account's subscriptions, shared by every feed and the Explore sheet. */
    val subscriptions: StateFlow<List<String>> = _subscriptions.asStateFlow()

    private val _moderated = MutableStateFlow<List<String>>(emptyList())
    val moderated: StateFlow<List<String>> = _moderated.asStateFlow()

    private val _subredditIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Icons of subscribed and moderated communities, by lowercase name. */
    val subredditIcons: StateFlow<Map<String, String>> = _subredditIcons.asStateFlow()

    private val _events = MutableSharedFlow<ContentEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ContentEvent> = _events.asSharedFlow()

    val isConfigured: Boolean get() = configStore.load() != null || configStore.demoMode

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reports [fullname] in the background (the sheet that asked is gone by then) and says how it went. */
    fun report(fullname: String, reason: ReportReason?, other: String? = null) {
        scope.launch {
            val result = runCatching { api.report(fullname, reason, other) }
            Toast.makeText(appContext, result.fold({ "Reported" }, { "Couldn't report: ${it.message}" }), Toast.LENGTH_SHORT).show()
        }
    }

    /** Sync's Watch: notify about [community]'s posts from now on, keeping the hourly check scheduled only while needed. */
    fun setWatching(community: String, on: Boolean) {
        if (on) watch.watch(community, since = System.currentTimeMillis() / 1000) else watch.unwatch(community)
        WatchWorker.schedule(appContext, !watch.isEmpty)
    }

    /** Sync's post Watch: notify about new comments on [post] from now on. */
    fun setWatchingPost(post: Post, on: Boolean) {
        if (on) watch.watchPost(post) else watch.unwatchPost(post.id)
        WatchWorker.schedule(appContext, !watch.isEmpty)
    }

    fun unwatchPost(id: String) {
        watch.unwatchPost(id)
        WatchWorker.schedule(appContext, !watch.isEmpty)
    }

    /** Settings › Backup now: the backed-up preferences, plus the accounts encrypted with [accountsPassword] if given. */
    fun exportBackup(accountsPassword: CharArray? = null): String = Backup.export(prefs, accountsPassword = accountsPassword)

    /** Whether there are signed-in accounts Backup now could include. */
    val hasBackupAccounts: Boolean get() = Backup.accountsIn(prefs) != null

    /** Where the copy taken before the last restore lives: private storage, excluded from any cloud backup. */
    private val preRestoreFile get() = java.io.File(appContext.noBackupFilesDir, "pre-restore-snapshot.json")

    /** When the copy taken before the last restore was made, or null if there's none to go back to. */
    fun preRestoreSnapshotCreated(): String? =
        preRestoreFile.takeIf { it.exists() }?.let { runCatching { Backup.snapshotCreated(it.readText()) }.getOrNull() }

    /**
     * Settings › Restore from backup. First saves everything as it is now (accounts included) so the restore can be
     * undone. The stores keep what they loaded in memory, so the caller must restart the app straight away.
     */
    fun restoreBackup(
        doc: BackupDocument,
        includeApiConfig: Boolean,
        parts: Set<BackupPart>,
        accounts: AccountsBackup?,
    ): RestoreResult {
        writeAtomically(preRestoreFile, Backup.snapshot(prefs))
        return Backup.restore(prefs, doc, includeApiConfig, parts, accounts)
    }

    /** Puts everything back as it was before the last restore. The caller must restart the app straight away. */
    fun undoRestore() {
        Backup.restoreSnapshot(prefs, preRestoreFile.readText())
        preRestoreFile.delete()
    }

    private fun writeAtomically(file: java.io.File, text: String) {
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "couldn't save a copy of the current data" }
        }
    }

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
        clearAccountState()
        _username.value = name
    }

    /** Forgets the active account and drops to logged out. */
    fun logout() {
        _username.value?.let(accountStore::remove)
        accountStore.clearSession()
        _accounts.value = accountStore.accounts.map { it.username }
        clearAccountState()
        _username.value = null
    }

    suspend fun refreshInboxCount() {
        if (_username.value == null) {
            _avatar.value = null
            return
        }
        runCatching { api.me() }.onSuccess {
            _inboxCount.value = it.inboxCount
            _avatar.value = it.iconUrl
        }
    }

    /** The account [refreshSubscriptions] last loaded, so opening feeds doesn't refetch every time. */
    private var subscriptionsLoadedFor: String? = null

    /** Loads subscriptions and moderated communities once per account; [force] reloads anyway. */
    suspend fun refreshSubscriptions(force: Boolean = false) {
        val user = _username.value
        if (user == null) {
            _subscriptions.value = emptyList()
            _moderated.value = emptyList()
            subscriptionsLoadedFor = null
            return
        }
        if (!force && subscriptionsLoadedFor == user) return
        runCatching { api.subscriptions() }.onSuccess { subs ->
            subscriptionsLoadedFor = user // only once it worked, so a failure retries next time
            _subscriptions.value = subs.map { it.name }
            _subredditIcons.value += subs.iconsByName()
        }
        runCatching { api.moderated() }.onSuccess { subs ->
            _moderated.value = subs.map { it.name }
            _subredditIcons.value += subs.iconsByName()
        }
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

    private fun clearAccountState() {
        _inboxCount.value = 0
        _avatar.value = null
        _subscriptions.value = emptyList()
        _moderated.value = emptyList()
        subscriptionsLoadedFor = null
    }

    private fun List<Subreddit>.iconsByName() = mapNotNull { sub -> sub.iconUrl?.let { sub.name.lowercase() to it } }.toMap()

    private fun forgetAllAccounts() {
        accountStore.removeAll()
        _accounts.value = emptyList()
        clearAccountState()
        _username.value = null
    }
}
