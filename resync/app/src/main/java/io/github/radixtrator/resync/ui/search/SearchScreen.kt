@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Listing
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.SearchSort
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import io.github.radixtrator.resync.data.SearchMode
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.ChipMenu
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchPostsViewModel(container: AppContainer, query: String, private val subreddit: String?) :
    ListingViewModel<Post>(container) {
    private val _query = MutableStateFlow(query)
    /** The query the results are for (the toolbar field may hold an edit not yet submitted). */
    val query = _query.asStateFlow()
    private val _sort = MutableStateFlow(SearchSort.RELEVANCE)
    val sort = _sort.asStateFlow()
    private val _mode = MutableStateFlow(SearchMode.SEARCH)
    val mode = _mode.asStateFlow()
    private val _target = MutableStateFlow("")
    val target = _target.asStateFlow()
    private val _restrict = MutableStateFlow(subreddit != null)
    val restrict = _restrict.asStateFlow()

    init {
        if (query.isNotBlank()) search(query)
    }

    override suspend fun fetch(after: String?): Listing<Post> {
        val q = _query.value.trim()
        if (q.isEmpty()) return Listing(emptyList(), null)
        val target = _target.value.trim().removePrefix("r/").removePrefix("/r/").removePrefix("u/").removePrefix("/u/")
        if (_mode.value != SearchMode.SEARCH && target.isEmpty()) return Listing(emptyList(), null)
        return when (_mode.value) {
            SearchMode.SEARCH -> container.api.searchPosts(q, subreddit.takeIf { _restrict.value }, _sort.value, after)
            SearchMode.SUBREDDIT -> container.api.searchPosts(q, target, _sort.value, after)
            SearchMode.CUSTOM_FEED -> container.api.searchPosts(q, null, _sort.value, after, multi = target)
            SearchMode.PROFILE -> container.api.searchPosts("author:$target $q", null, _sort.value, after)
            SearchMode.DOMAIN -> container.api.searchPosts("site:$target $q", null, _sort.value, after)
        }
    }

    override fun keyOf(item: Post) = item.fullname
    override fun postOf(item: Post) = item
    override fun withPost(item: Post, post: Post) = post

    fun search(query: String) {
        if (query != _query.value || _state.value.items.isEmpty()) {
            if (query.isNotBlank()) container.explore.recordSearch(query, subreddit.takeIf { _restrict.value })
        }
        _query.value = query
        updateItems { emptyList() }
        reload()
    }

    fun setSort(sort: SearchSort) {
        _sort.value = sort
        search(_query.value)
    }

    fun setMode(mode: SearchMode, target: String) {
        _mode.value = mode
        _target.value = target
        search(_query.value)
    }

    fun setRestrict(restrict: Boolean) {
        _restrict.value = restrict
        search(_query.value)
    }
}

class SearchCommunitiesViewModel(container: AppContainer, query: String) : ListingViewModel<Subreddit>(container) {
    private val _query = MutableStateFlow(query)

    init {
        if (query.isNotBlank()) reload()
    }

    override suspend fun fetch(after: String?): Listing<Subreddit> {
        val q = _query.value.trim()
        if (q.isEmpty()) return Listing(emptyList(), null)
        return container.api.searchSubreddits(q, after)
    }

    override fun keyOf(item: Subreddit) = item.fullname

    fun search(query: String) {
        _query.value = query
        updateItems { emptyList() }
        reload()
    }
}

@Composable
fun SearchScreen(initialQuery: String, subreddit: String?, container: AppContainer, communitiesTab: Boolean = false) {
    val nav = LocalNavigator.current
    val posts = viewModel(key = "posts") { SearchPostsViewModel(container, initialQuery, subreddit) }
    val communities = viewModel(key = "communities") { SearchCommunitiesViewModel(container, initialQuery) }
    val postState by posts.state.collectAsStateWithLifecycle()
    val communityState by communities.state.collectAsStateWithLifecycle()
    val restrict by posts.restrict.collectAsStateWithLifecycle()
    val mode by posts.mode.collectAsStateWithLifecycle()
    val target by posts.target.collectAsStateWithLifecycle()
    val sort by posts.sort.collectAsStateWithLifecycle()
    val searched by posts.query.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var tab by rememberSaveable { mutableIntStateOf(if (communitiesTab) 1 else 0) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val callbacks = remember(posts) { posts.postCallbacks() }
    // Like the feeds' toolbar: it slides away as the list scrolls and comes back on scroll-up.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    MessageEffect(postState.message, snackbar, posts::messageShown)
    LaunchedEffect(Unit) { if (initialQuery.isBlank()) focus.requestFocus() }

    fun submit() {
        if (query.isBlank()) return
        focusManager.clearFocus()
        posts.search(query)
        communities.search(query)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // The toolbar keeps the editable query; the large header below names what the results are for.
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(if (subreddit != null && restrict) "Search r/$subreddit" else "Search Reddit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = { if (tab == 0) SearchSortMenu(posts::setSort) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Posts") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Communities") })
            }
            Box(Modifier.fillMaxSize()) {
                if (tab == 0) {
                    PagedList(
                        state = postState.copy(items = postState.items.visiblePosts { it }),
                        key = { it.fullname },
                        onLoadMore = posts::loadMore,
                        onRetry = posts::retry,
                        emptyText = if (searched.isBlank()) "Search for posts" else "No posts found",
                        // Sync's large title is the quoted query, so there's no header until something is searched.
                        header = if (searched.isBlank()) null else {
                            {
                                item(key = "header") {
                                    LargeHeader("\"$searched\"") {
                                        ChipMenu(sort.label, searchSortIcon(sort), SearchSort.entries, { it.label }, posts::setSort)
                                        HeaderChip(if (mode == SearchMode.SEARCH) "Options" else "${mode.label}: $target", Icons.Outlined.Settings, { optionsOpen = true })
                                        if (subreddit != null && mode == SearchMode.SEARCH) {
                                            HeaderChip(
                                                "Only in r/$subreddit",
                                                if (restrict) Icons.Outlined.Check else null,
                                                { posts.setRestrict(!restrict) },
                                                container = if (restrict) {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) { post -> PostListItem(post, showSubreddit = true, callbacks = callbacks) }
                } else {
                    PagedList(
                        state = communityState,
                        key = { it.fullname },
                        onLoadMore = communities::loadMore,
                        onRetry = communities::retry,
                        emptyText = if (query.isBlank()) "Search for communities" else "No communities found",
                    ) { sub -> CommunityRow(sub) { nav.push(FeedRoute(sub.name)) } }
                }
            }
        }
    }

    if (optionsOpen) {
        SearchOptionsSheet(
            initial = if (mode == SearchMode.SEARCH) searched else target,
            onDismiss = { optionsOpen = false },
            onChange = { mode, text ->
                optionsOpen = false
                when (mode) {
                    OptionsMode.SEARCH, OptionsMode.SEARCH_SUBS -> {
                        query = text
                        posts.setMode(SearchMode.SEARCH, "")
                        submit()
                        tab = if (mode == OptionsMode.SEARCH_SUBS) 1 else 0
                    }
                    // The other modes keep the query and narrow it: inside a community, a custom feed
                    // ("user/feed"), a user's posts or a site's links.
                    OptionsMode.SUBREDDIT -> posts.setMode(SearchMode.SUBREDDIT, text)
                    OptionsMode.CUSTOM_FEED -> posts.setMode(SearchMode.CUSTOM_FEED, text)
                    OptionsMode.PROFILE -> posts.setMode(SearchMode.PROFILE, text)
                    OptionsMode.DOMAIN -> posts.setMode(SearchMode.DOMAIN, text)
                }
            },
        )
    }
}

@Composable
private fun CommunityRow(sub: Subreddit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
            sub.iconUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("r/${sub.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${Format.count(sub.subscribers)} members" + if (sub.isSubscribed) " · Joined" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub.publicDescription.isNotBlank()) {
                Text(sub.publicDescription, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SearchSortMenu(onPick: (SearchSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SearchSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label) }, onClick = { open = false; onPick(sort) }) }
        }
    }
}

private fun searchSortIcon(sort: SearchSort): ImageVector = when (sort) {
    SearchSort.RELEVANCE -> AppIcons.Sort
    SearchSort.HOT -> AppIcons.Flame
    SearchSort.NEW -> AppIcons.Burst
    SearchSort.TOP_WEEK, SearchSort.TOP_ALL -> AppIcons.Bars
    SearchSort.COMMENTS -> AppIcons.Comment
}

/** The modes of Sync's search Options ("Edit current") sheet. */
private enum class OptionsMode(val label: String, val hint: String) {
    SEARCH("Search", "Search Reddit"),
    SUBREDDIT("Subreddit", "Subreddit name"),
    CUSTOM_FEED("Custom feed", "username/feedname"),
    SEARCH_SUBS("Search subs", "Search communities"),
    PROFILE("Profile", "Username"),
    DOMAIN("Domain", "example.com"),
}

/**
 * Sync's search Options: mode chips, one input, Cancel / Change. Search and Search subs re-run the
 * search here (on the Posts or Communities tab); the others narrow the same query to a community, custom feed, user or site.
 */
@Composable
private fun SearchOptionsSheet(initial: String, onDismiss: () -> Unit, onChange: (OptionsMode, String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(OptionsMode.SEARCH) }
    var text by rememberSaveable { mutableStateOf(initial) }
    fun change() {
        val value = text.trim()
        if (value.isNotEmpty()) onChange(mode, value)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Edit current", topPadding = 0.dp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionsMode.entries.forEach { option ->
                    FilterChip(selected = option == mode, onClick = { mode = option }, label = { Text(option.label) })
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(mode.hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { change() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { change() }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Change") }
            }
        }
    }
}
