@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.data.ExploreState
import io.github.radixtrator.resync.data.Recent
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.feed.SubredditInfoSheet
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** BROWSE opens on the feed lists (Explore tab); SEARCH focuses the field and shows recents (search button, FAB long-press). */
enum class ExploreMode { BROWSE, SEARCH }

/**
 * Sync's Explore: a sheet to jump between feeds (sync-reference/SYNC_PARITY_LOG.md §3.11).
 * Empty query shows Favorites, Feeds, Subscriptions, Moderated and Most visited (or, from search,
 * "Recently searched & visited"); typing filters subscriptions, suggests communities and offers
 * Open r/…, Open u/… and a full search. Each row's ⋮ favorites it, makes it the default, and so on.
 */
@Composable
fun ExploreSheet(
    container: AppContainer,
    mode: ExploreMode,
    onDismiss: () -> Unit,
    onOpenFeed: (String?) -> Unit,
    /** The community being viewed, for a "Search r/…" button beside "Search all". */
    subreddit: String? = null,
) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val explore by container.explore.state.collectAsStateWithLifecycle()
    val subscriptions by container.subscriptions.collectAsStateWithLifecycle()
    val moderated by container.moderated.collectAsStateWithLifecycle()
    val icons by container.subredditIcons.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var folded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var suggestions by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
    var aboutFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = mode == ExploreMode.SEARCH)
    val q = query.trim().removePrefix("/").removePrefix("r/")

    LaunchedEffect(Unit) { if (mode == ExploreMode.SEARCH) focus.requestFocus() }
    LaunchedEffect(q) {
        suggestions = emptyList()
        if (q.length < 2) return@LaunchedEffect
        delay(300) // debounce typing
        runCatching { container.api.searchSubreddits(q, after = null) }.onSuccess { suggestions = it.items.take(10) }
    }

    fun close(action: () -> Unit) {
        onDismiss()
        action()
    }

    val rowMenu = RowMenu(
        state = explore,
        subscribed = subscriptions,
        onToggleFavorite = container.explore::toggleFavorite,
        onSetDefault = container.explore::setDefault,
        onAbout = { aboutFor = it },
        onUnsubscribe = { name ->
            scope.launch {
                runCatching { container.api.setSubscribed(name, false) }
                container.refreshSubscriptions(force = true)
            }
        },
        onResetVisits = container.explore::resetVisits,
    )

    fun LazyListScope.section(section: ExploreSection, names: List<String>, label: (String) -> String = Format::feedName) {
        if (section in explore.hidden || names.isEmpty()) return
        val open = section.name !in folded
        header(section.label, expanded = open) { folded = if (open) folded + section.name else folded - section.name }
        if (open) {
            items(names, key = { "${section.name}:$it" }) { name ->
                FeedRow(name, label(name), icons[name.lowercase()], explore.isDefault(name), rowMenu, section) { onOpenFeed(name.ifEmpty { null }) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SearchField(
            value = query,
            onChange = { query = it },
            onSubmit = { if (q.isNotEmpty()) close { nav.push(SearchRoute(query = q)) } },
            menu = { ExploreOptionsMenu(container, explore) },
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
        )
        LazyColumn(Modifier.navigationBarsPadding().imePadding().padding(top = 8.dp)) {
            when {
                q.isNotEmpty() -> searchResults(
                    q, subscriptions, suggestions, icons, explore, rowMenu, onOpenFeed,
                    subreddit = subreddit,
                    onSearch = { inSub -> close { nav.push(SearchRoute(query = q, subreddit = inSub)) } },
                    onOpenUser = { close { nav.push(UserRoute(q)) } },
                )
                mode == ExploreMode.SEARCH && explore.recents.isNotEmpty() -> {
                    header("Recently searched & visited")
                    items(explore.recents, key = { "recent:${it.subreddit}:${it.query}" }) { recent ->
                        RecentRow(recent, icons) {
                            if (recent.query == null) onOpenFeed(recent.subreddit?.ifEmpty { null })
                            else close { nav.push(SearchRoute(recent.query, recent.subreddit)) }
                        }
                    }
                }
                else -> {
                    section(ExploreSection.FAVORITES, explore.favorites)
                    section(ExploreSection.FEEDS, listOf("", "popular", "all"))
                    section(
                        ExploreSection.SUBSCRIPTIONS,
                        if (explore.duplicateFavorites) subscriptions else subscriptions.filterNot(explore::isFavorite),
                    )
                    section(ExploreSection.MODERATED, moderated)
                    val visited = explore.mostVisited()
                    section(ExploreSection.MOST_VISITED, visited.map { it.name }) { name ->
                        "${Format.feedName(name)} (${visited.first { it.name == name }.count})"
                    }
                }
            }
        }
    }

    aboutFor?.let { name ->
        var about by remember(name) { mutableStateOf<Subreddit?>(null) }
        LaunchedEffect(name) { runCatching { container.api.subredditAbout(name) }.onSuccess { about = it } }
        SubredditInfoSheet(
            about = about,
            onDismiss = { aboutFor = null },
            onToggleSubscribe = {
                val sub = about ?: return@SubredditInfoSheet
                about = sub.copy(isSubscribed = !sub.isSubscribed)
                scope.launch {
                    runCatching { container.api.setSubscribed(sub.name, !sub.isSubscribed) }.onFailure { about = sub }
                    container.refreshSubscriptions(force = true)
                }
            },
            onSubmit = {
                aboutFor = null
                close { nav.push(SubmitRoute(name)) }
            },
        )
    }
}

private fun LazyListScope.searchResults(
    q: String,
    subscriptions: List<String>,
    suggestions: List<Subreddit>,
    icons: Map<String, String>,
    explore: ExploreState,
    rowMenu: RowMenu,
    onOpenFeed: (String?) -> Unit,
    subreddit: String?,
    onSearch: (String?) -> Unit,
    onOpenUser: () -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onSearch(null) }, modifier = Modifier.weight(1f)) { Text("Search all") }
            if (subreddit != null) {
                Button(
                    onClick = { onSearch(subreddit) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("Search $subreddit", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    val subscribed = subscriptions.filter { it.contains(q, ignoreCase = true) }
    if (subscribed.isNotEmpty()) {
        header("Subscribed")
        items(subscribed, key = { "mine:$it" }) { name ->
            FeedRow(name, name, icons[name.lowercase()], explore.isDefault(name), rowMenu, null) { onOpenFeed(name) }
        }
    }
    if (suggestions.isNotEmpty()) {
        header("Suggested subreddit results")
        items(suggestions, key = { "suggest:${it.name}" }) { sub ->
            FeedRow(sub.name, sub.name, sub.iconUrl, explore.isDefault(sub.name), rowMenu, null, trailing = "${Format.count(sub.subscribers)} subs") {
                onOpenFeed(sub.name)
            }
        }
    }
    header("Actions")
    item { ActionRow("Open r/$q", AppIcons.Compass) { onOpenFeed(q) } }
    item { ActionRow("Open u/$q", Icons.Outlined.Person, onOpenUser) }
}

/** What a row's ⋮ can do; built once per sheet. */
private class RowMenu(
    val state: ExploreState,
    val subscribed: List<String>,
    val onToggleFavorite: (String) -> Unit,
    val onSetDefault: (String) -> Unit,
    val onAbout: (String) -> Unit,
    val onUnsubscribe: (String) -> Unit,
    val onResetVisits: (String) -> Unit,
)

/** A feed or community row: icon, name, optional "Default" tag or count, and Sync's ⋮ menu. */
@Composable
private fun FeedRow(
    name: String,
    label: String,
    iconUrl: String?,
    isDefault: Boolean,
    menu: RowMenu,
    section: ExploreSection?,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isCommunity = name.lowercase() !in setOf("", "popular", "all")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedIcon(name, iconUrl)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (isDefault) {
            Text(
                "Default",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Options for ${Format.feedName(name)}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val pick = { action: () -> Unit -> menuOpen = false; action() }
                DropdownMenuItem(
                    text = { Text(if (menu.state.isFavorite(name)) "Unfavorite" else "Favorite") },
                    onClick = { pick { menu.onToggleFavorite(name) } },
                )
                if (!isDefault) DropdownMenuItem(text = { Text("Set as default") }, onClick = { pick { menu.onSetDefault(name) } })
                if (isCommunity) DropdownMenuItem(text = { Text("About") }, onClick = { pick { menu.onAbout(name) } })
                if (isCommunity && menu.subscribed.any { it.equals(name, ignoreCase = true) }) {
                    DropdownMenuItem(text = { Text("Unsubscribe") }, onClick = { pick { menu.onUnsubscribe(name) } })
                }
                if (section == ExploreSection.MOST_VISITED) {
                    DropdownMenuItem(text = { Text("Reset viewed") }, onClick = { pick { menu.onResetVisits(name) } })
                }
            }
        }
    }
}

@Composable
private fun RecentRow(recent: Recent, icons: Map<String, String>, onClick: () -> Unit) {
    val name = recent.subreddit.orEmpty()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recent.query != null) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null) }
        } else {
            FeedIcon(name, icons[name.lowercase()])
        }
        Spacer(Modifier.width(16.dp))
        val text = when {
            recent.query == null -> Format.feedName(name)
            recent.subreddit != null -> "\"${recent.query}\" in r/${recent.subreddit}"
            else -> "\"${recent.query}\""
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The front page, Popular and All get glyphs; communities their icon or a lettered circle. */
@Composable
private fun FeedIcon(name: String, iconUrl: String?) {
    val glyph: ImageVector? = when (name.lowercase()) {
        "" -> Icons.Outlined.Home
        "popular" -> AppIcons.TrendingUp
        "all" -> AppIcons.Bars
        else -> null
    }
    if (glyph != null) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(glyph, null) }
        return
    }
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (iconUrl != null) AsyncImage(iconUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/** The ⋮ beside Explore's search field: which sections show, plus housekeeping. */
@Composable
private fun ExploreOptionsMenu(container: AppContainer, state: ExploreState) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Explore options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExploreSection.entries.forEach { section ->
                val visible = section !in state.hidden
                DropdownMenuItem(
                    text = { Text("Show ${section.label.lowercase()}") },
                    trailingIcon = { Checkbox(checked = visible, onCheckedChange = null) },
                    onClick = { container.explore.setVisible(section, !visible) },
                )
            }
            DropdownMenuItem(
                text = { Text("Duplicate favorites") },
                trailingIcon = { Checkbox(checked = state.duplicateFavorites, onCheckedChange = null) },
                onClick = { container.explore.setDuplicateFavorites(!state.duplicateFavorites) },
            )
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Reload subscriptions") }, onClick = {
                open = false
                scope.launch { container.refreshSubscriptions(force = true) }
            })
            DropdownMenuItem(text = { Text("Clear search history") }, onClick = { open = false; container.explore.clearRecents() })
            DropdownMenuItem(text = { Text("Clear most visited") }, onClick = { open = false; container.explore.clearVisits() })
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear") } else menu()
    }
}

private fun LazyListScope.header(title: String, expanded: Boolean? = null, onToggle: (() -> Unit)? = null) {
    item(key = "header:$title") {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (expanded != null) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
