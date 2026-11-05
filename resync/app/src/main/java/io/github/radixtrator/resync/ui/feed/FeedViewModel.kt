package io.github.radixtrator.resync.ui.feed

import androidx.lifecycle.viewModelScope
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.ListingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(container: AppContainer, val subreddit: String?) : ListingViewModel<Post>(container) {

    private val _sort = MutableStateFlow(defaultSort(container.username.value))
    val sort = _sort.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<String>>(emptyList())
    val subscriptions = _subscriptions.asStateFlow()

    private val _about = MutableStateFlow<Subreddit?>(null)
    val about = _about.asStateFlow()

    /** A single community, as opposed to the front page, r/all, r/popular or a multi. */
    val isCommunity = subreddit != null && subreddit.lowercase() !in setOf("all", "popular") && '+' !in subreddit

    /** "Best" only exists for the logged-in front page. */
    val sortOptions: List<Sort>
        get() = if (subreddit == null && container.username.value != null) Sort.entries else Sort.entries - Sort.BEST

    init {
        viewModelScope.launch {
            // Emits immediately, then on every account switch, so this is also the first load.
            container.username.collect { name ->
                if (subreddit == null || _sort.value !in sortOptions) _sort.value = defaultSort(name)
                reload()
                loadSubscriptions(name)
                container.refreshInboxCount()
            }
        }
    }

    private fun defaultSort(user: String?) = if (subreddit == null && user != null) Sort.BEST else Sort.HOT

    override suspend fun fetch(after: String?): Listing<Post> = container.api.posts(subreddit, _sort.value, after)
    override fun keyOf(item: Post) = item.fullname
    override fun postOf(item: Post) = item
    override fun withPost(item: Post, post: Post) = post

    fun setSort(sort: Sort) {
        if (sort == _sort.value) return
        _sort.value = sort
        updateItems { emptyList() }
        reload()
    }

    private fun loadSubscriptions(user: String?) {
        if (user == null) {
            _subscriptions.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching { container.api.subscriptions() }.onSuccess { _subscriptions.value = it }
        }
    }

    fun loadAbout() {
        if (!isCommunity || _about.value != null) return
        viewModelScope.launch {
            runCatching { container.api.subredditAbout(subreddit!!) }
                .onSuccess { _about.value = it }
                .onFailure { showMessage("Couldn't load community info: ${it.message}") }
        }
    }

    fun toggleSubscribe() {
        val sub = _about.value ?: return
        if (!requireLogin()) return
        _about.value = sub.copy(isSubscribed = !sub.isSubscribed, subscribers = sub.subscribers + if (sub.isSubscribed) -1 else 1)
        viewModelScope.launch {
            runCatching { container.api.setSubscribed(sub.name, !sub.isSubscribed) }
                .onSuccess { loadSubscriptions(container.username.value) }
                .onFailure {
                    _about.value = sub
                    showMessage("Couldn't update subscription: ${it.message}")
                }
        }
    }
}
