package io.github.radixtrator.resync.ui.feed

import androidx.lifecycle.viewModelScope
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.SortKind
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.ListingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A feed's posts, or on the "Comments" sort its newest comments. */
class FeedViewModel(container: AppContainer, val subreddit: String?, val domain: String? = null) : ListingViewModel<ProfileItem>(container) {

    /** The front page itself, as opposed to a community, a multi or a domain's feed. */
    private val frontPage = subreddit == null && domain == null

    /** The key this feed's saved sort (and layout) is stored under; a domain's is kept apart from the front page's. */
    val feedKey: String? = domain?.let { "domain:$it" } ?: subreddit

    private val _sort = MutableStateFlow(defaultSort(container.username.value))
    val sort = _sort.asStateFlow()

    /** What this feed opened on, so picking it again doesn't ask to save it. */
    val openingSort = _sort.value

    private val _about = MutableStateFlow<Subreddit?>(null)
    val about = _about.asStateFlow()

    /** A single community, as opposed to the front page, r/all, r/popular or a multi. */
    val isCommunity = subreddit != null && subreddit.lowercase() !in setOf("all", "popular") && '+' !in subreddit

    /** "Best" only exists for the logged-in front page, and domains have no comment stream. */
    val sortOptions: List<SortKind> get() = sortOptionsFor(container.username.value)

    private fun sortOptionsFor(user: String?): List<SortKind> = when {
        domain != null -> SortKind.entries - SortKind.BEST - SortKind.COMMENTS
        frontPage && user != null -> SortKind.entries
        else -> SortKind.entries - SortKind.BEST
    }

    init {
        viewModelScope.launch {
            // Emits immediately, then on every account switch, so this is also the first load.
            container.username.collect { name ->
                if (frontPage || _sort.value.kind !in sortOptions) _sort.value = defaultSort(name)
                reload()
                container.refreshSubscriptions()
                container.refreshInboxCount()
            }
        }
        // The community header shows the icon and subscriber count, like Sync's.
        loadAbout(quiet = true)
        // Feeds Explore's "Most visited" and "Recently searched & visited" (domains aren't feeds Explore lists).
        if (domain == null) container.explore.recordVisit(subreddit)
    }

    /** The sort saved for this feed, else Reddit's own default: Best on the logged-in front page, Hot elsewhere. */
    private fun defaultSort(user: String?): Sort =
        container.settings.settings.value.sortFor(feedKey)?.takeIf { it.kind in sortOptionsFor(user) }
            ?: if (frontPage && user != null) Sort.BEST else Sort.HOT

    override suspend fun fetch(after: String?): Listing<ProfileItem> {
        if (_sort.value.kind == SortKind.COMMENTS) return container.api.feedComments(subreddit, after)
        val page = container.api.posts(subreddit, _sort.value, after, domain = domain)
        return Listing(page.items.map(ProfileItem::PostItem), page.after)
    }

    override fun keyOf(item: ProfileItem) = item.fullname
    override fun postOf(item: ProfileItem) = (item as? ProfileItem.PostItem)?.post
    override fun withPost(item: ProfileItem, post: Post) = ProfileItem.PostItem(post)

    /** Opens this feed on [sort] from now on. */
    fun saveSort(sort: Sort) {
        container.settings.update { it.copy(feedSorts = it.feedSorts + (Settings.feedKey(feedKey) to sort)) }
    }

    fun setSort(sort: Sort) {
        if (sort == _sort.value) return
        _sort.value = sort
        updateItems { emptyList() }
        reload()
    }

    /** [quiet]: the header can do without it, so only an explicit About tap reports failures. */
    fun loadAbout(quiet: Boolean = false) {
        if (!isCommunity || _about.value != null) return
        viewModelScope.launch {
            runCatching { container.api.subredditAbout(subreddit!!) }
                .onSuccess { _about.value = it }
                .onFailure { if (!quiet) showMessage("Couldn't load community info: ${it.message}") }
        }
    }

    fun toggleSubscribe() {
        val sub = _about.value ?: return
        if (!requireLogin()) return
        _about.value = sub.copy(isSubscribed = !sub.isSubscribed, subscribers = sub.subscribers + if (sub.isSubscribed) -1 else 1)
        viewModelScope.launch {
            runCatching { container.api.setSubscribed(sub.name, !sub.isSubscribed) }
                .onSuccess { container.refreshSubscriptions(force = true) }
                .onFailure {
                    _about.value = sub
                    showMessage("Couldn't update subscription: ${it.message}")
                }
        }
    }
}
