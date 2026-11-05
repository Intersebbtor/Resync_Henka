@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.ListingViewModel
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PagedList
import io.github.radixtrator.resync.ui.common.PostListItem
import io.github.radixtrator.resync.ui.common.postCallbacks
import io.github.radixtrator.resync.ui.common.visiblePosts
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchPostsViewModel(container: AppContainer, query: String, private val subreddit: String?) :
    ListingViewModel<Post>(container) {
    private val _query = MutableStateFlow(query)
    private val _sort = MutableStateFlow(SearchSort.RELEVANCE)
    val sort = _sort.asStateFlow()
    private val _restrict = MutableStateFlow(subreddit != null)
    val restrict = _restrict.asStateFlow()

    init {
        if (query.isNotBlank()) search(query)
    }

    override suspend fun fetch(after: String?): Listing<Post> {
        val q = _query.value.trim()
        if (q.isEmpty()) return Listing(emptyList(), null)
        return container.api.searchPosts(q, subreddit.takeIf { _restrict.value }, _sort.value, after)
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
fun SearchScreen(initialQuery: String, subreddit: String?, container: AppContainer) {
    val nav = LocalNavigator.current
    val posts = viewModel(key = "posts") { SearchPostsViewModel(container, initialQuery, subreddit) }
    val communities = viewModel(key = "communities") { SearchCommunitiesViewModel(container, initialQuery) }
    val postState by posts.state.collectAsStateWithLifecycle()
    val communityState by communities.state.collectAsStateWithLifecycle()
    val restrict by posts.restrict.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val callbacks = remember(posts) { posts.postCallbacks() }

    MessageEffect(postState.message, snackbar, posts::messageShown)
    LaunchedEffect(Unit) { if (initialQuery.isBlank()) focus.requestFocus() }

    fun submit() {
        if (query.isBlank()) return
        focusManager.clearFocus()
        posts.search(query)
        communities.search(query)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
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
            if (tab == 0 && subreddit != null) {
                FilterChip(
                    selected = restrict,
                    onClick = { posts.setRestrict(!restrict) },
                    label = { Text("Only in r/$subreddit") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                if (tab == 0) {
                    PagedList(
                        state = postState.copy(items = postState.items.visiblePosts { it }),
                        key = { it.fullname },
                        onLoadMore = posts::loadMore,
                        onRetry = posts::retry,
                        emptyText = if (query.isBlank()) "Search for posts" else "No posts found",
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
