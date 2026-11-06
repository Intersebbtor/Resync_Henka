@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.home

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.feed.AboutAction
import io.github.radixtrator.resync.ui.feed.CommunityActionSheet
import io.github.radixtrator.resync.ui.feed.SubredditInfoSheet
import io.github.radixtrator.resync.ui.feed.rememberWatchToggle
import io.github.radixtrator.resync.ui.feed.runAboutAction
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** BROWSE opens on the feed lists (Explore tab); SEARCH focuses the field and shows recents (search button, FAB long-press). */
enum class ExploreMode { BROWSE, SEARCH }

// Sync's measurements (sync-reference dumps 36–40): 42dp rows with 14sp names, 51dp section headers.
private val ROW_HEIGHT = 42.dp
private val HEADER_HEIGHT = 51.dp
private val FrontpageOrange = Color(0xFFFF4500)
private const val TAG = "ResyncExplore"

/**
 * Sync's Explore: a sheet to jump between feeds (sync-reference/SYNC_PARITY_LOG.md §3.11).
 * Empty query shows Favorites, Subscriptions, Moderated and Top (Feeds is opt-in), or, from search,
 * "Recently searched & visited"; typing filters subscriptions, suggests communities and offers
 * Open u/…, Open r/… and a full search. Each row's ⋮ favorites it, makes it the default, and so on.
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
    val folds = Folds(folded) { title -> folded = if (title in folded) folded - title else folded + title }
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
        runCatching { container.api.searchSubreddits(q, after = null) }
            .onSuccess { suggestions = it.items.take(10) }
            .onFailure { if (it !is CancellationException) Log.w(TAG, "Community suggestions for \"$q\" failed", it) }
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

    // Your multireddits; each opens as the "a+b+c" feed of its communities. Empty when logged out or on error.
    val customFeeds by androidx.compose.runtime.produceState(emptyList<io.github.radixtrator.resync.data.Multi>()) {
        value = runCatching { container.api.multis() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
            .filter { it.subreddits.isNotEmpty() }
            .distinctBy { it.feedName }
    }

    fun LazyListScope.section(section: ExploreSection, names: List<String>, label: (String) -> String = Format::feedName) {
        if (section in explore.hidden || names.isEmpty()) return
        group(section.header, folds) {
            items(names, key = { "${section.name}:$it" }) { name ->
                FeedRow(name, label(name), icons[name.lowercase()], explore.isDefault(name), rowMenu, section) { onOpenFeed(name.ifEmpty { null }) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { SheetHandle() },
    ) {
        SearchField(
            value = query,
            onChange = { query = it },
            onSubmit = { if (q.isNotEmpty()) close { nav.push(SearchRoute(query = q)) } },
            menu = { ExploreOptionsMenu(container, explore) },
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
        )
        HorizontalDivider(Modifier.padding(top = 16.dp))
        // Always full height, so the sheet doesn't jump as results come and go while typing.
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            // The list keeps its place by the first visible item; anchoring on this one keeps a
            // section that appears at the top (a first favorite) in view instead of just above it.
            item(key = "top") { Spacer(Modifier.height(1.dp)) }
            when {
                q.isNotEmpty() -> searchResults(
                    q, subscriptions, suggestions, icons, rowMenu, folds, onOpenFeed,
                    subreddit = subreddit,
                    onSearch = { inSub -> close { nav.push(SearchRoute(query = q, subreddit = inSub)) } },
                    onSearchCommunities = { close { nav.push(SearchRoute(query = q, communities = true)) } },
                    onOpenUser = { close { nav.push(UserRoute(q)) } },
                )
                mode == ExploreMode.SEARCH && explore.recents.isNotEmpty() -> group("Recently searched & visited", folds) {
                    items(explore.recents, key = { "recent:${it.subreddit}:${it.query}" }) { recent ->
                        val name = recent.subreddit.orEmpty()
                        if (recent.query == null) {
                            FeedRow(name, Format.feedName(name), icons[name.lowercase()], isDefault = false, rowMenu, null) {
                                onOpenFeed(name.ifEmpty { null })
                            }
                        } else {
                            QueryRow(recent, icons[name.lowercase()]) { close { nav.push(SearchRoute(recent.query, recent.subreddit)) } }
                        }
                    }
                }
                else -> {
                    section(ExploreSection.FAVORITES, explore.favorites)
                    section(ExploreSection.FEEDS, listOf("", "popular", "all"))
                    section(ExploreSection.CUSTOM_FEEDS, customFeeds.map { it.feedName }) { feed -> customFeeds.first { it.feedName == feed }.name }
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

    val context = LocalContext.current
    val watched by container.watch.watched.collectAsStateWithLifecycle()
    val toggleWatch = rememberWatchToggle(container) { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    var aboutSheet by remember { mutableStateOf<Pair<String, AboutAction>?>(null) }
    aboutFor?.let { name ->
        var about by remember(name) { mutableStateOf<Subreddit?>(null) }
        LaunchedEffect(name) { runCatching { container.api.subredditAbout(name) }.onSuccess { about = it } }
        SubredditInfoSheet(
            about = about,
            watching = name.lowercase() in watched,
            favorite = explore.isFavorite(name),
            onDismiss = { aboutFor = null },
            onToggleWatch = { toggleWatch(name) },
            onToggleFavorite = { container.explore.toggleFavorite(name) },
            onAction = { action ->
                aboutFor = null
                when (action) {
                    AboutAction.SHARE, AboutAction.MESSAGE_MODS -> close { nav.runAboutAction(action, name) }
                    else -> aboutSheet = name to action
                }
            },
            onToggleSubscribe = {
                val sub = about ?: return@SubredditInfoSheet
                about = sub.copy(isSubscribed = !sub.isSubscribed)
                scope.launch {
                    runCatching { container.api.setSubscribed(sub.name, !sub.isSubscribed) }.onFailure { about = sub }
                    container.refreshSubscriptions(force = true)
                }
            },
        )
    }
    aboutSheet?.let { (name, action) ->
        CommunityActionSheet(container, name, action, onDismiss = { aboutSheet = null }, onNavigate = { route -> close { nav.push(route) } })
    }
}

private fun LazyListScope.searchResults(
    q: String,
    subscriptions: List<String>,
    suggestions: List<Subreddit>,
    icons: Map<String, String>,
    rowMenu: RowMenu,
    folds: Folds,
    onOpenFeed: (String?) -> Unit,
    subreddit: String?,
    onSearch: (String?) -> Unit,
    onSearchCommunities: () -> Unit,
    onOpenUser: () -> Unit,
) {
    item(key = "search-buttons") {
        Column {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchButton("Search all", Modifier.weight(1f)) { onSearch(null) }
                if (subreddit != null) SearchButton("Search r/$subreddit", Modifier.weight(1f)) { onSearch(subreddit) }
            }
            HorizontalDivider()
        }
    }
    val subscribed = subscriptions.filter { it.contains(q, ignoreCase = true) }
    if (subscribed.isNotEmpty()) {
        group("Subscribed", folds) {
            items(subscribed, key = { "mine:$it" }) { name ->
                FeedRow(name, name, icons[name.lowercase()], isDefault = false, rowMenu, null, match = q) { onOpenFeed(name) }
            }
        }
    }
    if (suggestions.isNotEmpty()) {
        group("Suggested subreddit results", folds, action = "Search all subs" to onSearchCommunities) {
            items(suggestions, key = { "suggest:${it.name}" }) { sub ->
                FeedRow(sub.name, sub.name, sub.iconUrl, isDefault = false, rowMenu, null, match = q, trailing = "${subscriberCount(sub.subscribers)} subs") {
                    onOpenFeed(sub.name)
                }
            }
        }
    }
    group("Actions", folds) {
        item(key = "open-user") { ActionRow("Open u/$q", Icons.Outlined.Person, onOpenUser) }
        item(key = "open-sub") { ActionRow("Open r/$q", AppIcons.Compass) { onOpenFeed(q) } }
    }
}

/** Sync's subscriber counts: "9664", "152k", "33.58m". */
private fun subscriberCount(n: Int): String = when {
    n < 10_000 -> n.toString()
    n < 1_000_000 -> "${n / 1_000}k"
    else -> String.format(Locale.ROOT, "%.2fm", n / 1_000_000.0)
}

/** Which headers are folded, by title; kept for as long as the sheet is open. */
private class Folds(val folded: Set<String>, val toggle: (String) -> Unit)

/** A header with Sync's fold arrow, then [content] while it's open. [action] is a link at the header's end. */
private fun LazyListScope.group(
    title: String,
    folds: Folds,
    action: Pair<String, () -> Unit>? = null,
    content: LazyListScope.() -> Unit,
) {
    val open = title !in folds.folded
    item(key = "header:$title") { SectionHeader(title, open, action) { folds.toggle(title) } }
    if (open) content()
}

@Composable
private fun SectionHeader(title: String, open: Boolean, action: Pair<String, () -> Unit>?, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT).clickable(onClick = onToggle).padding(start = 16.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) ->
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxHeight().clickable(onClick = onClick).wrapContentHeight().padding(horizontal = 8.dp),
            )
        }
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            if (open) "Collapse $title" else "Expand $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp).size(18.dp),
        )
    }
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

/**
 * A feed or community row: icon, name, optional "Default" tag or count, and Sync's ⋮ menu.
 * [match] is bolded in the name, as in Sync's search results.
 */
@Composable
private fun FeedRow(
    name: String,
    label: String,
    iconUrl: String?,
    isDefault: Boolean,
    menu: RowMenu,
    section: ExploreSection?,
    match: String? = null,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isCommunity = name.lowercase() !in setOf("", "popular", "all")
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable(onClick = onClick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedIcon(name, iconUrl)
        Spacer(Modifier.width(16.dp))
        Text(
            emphasize(label, match),
            style = MaterialTheme.typography.bodyMedium,
            // Sync's "Highlight default": the default feed reads in the accent color.
            color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val note = if (isDefault) "Default" else trailing
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
        Box {
            RowMenuButton("Options for ${Format.feedName(name)}") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val pick = { action: () -> Unit -> menuOpen = false; action() }
                DropdownMenuItem(
                    text = { Text(if (menu.state.isFavorite(name)) "Unfavorite" else "Favorite") },
                    onClick = { pick { menu.onToggleFavorite(name) } },
                )
                if (!menu.state.isDefault(name)) DropdownMenuItem(text = { Text("Set as default") }, onClick = { pick { menu.onSetDefault(name) } })
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

private fun emphasize(label: String, match: String?): AnnotatedString = buildAnnotatedString {
    append(label)
    val at = if (match.isNullOrEmpty()) -1 else label.indexOf(match, ignoreCase = true)
    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + match!!.length)
}

/**
 * Sync's row ⋮: a small gray 18dp glyph in a 42dp square, under Material's 48dp minimum, which
 * would make every row taller.
 */
@Composable
private fun RowMenuButton(description: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(onClick = onClick, modifier = Modifier.size(ROW_HEIGHT)) {
            Icon(Icons.Default.MoreVert, description, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

/** A past search: `"voice" in /r/ObsidianMD` with the community's icon, or a search glyph for all of Reddit. */
@Composable
private fun QueryRow(recent: Recent, iconUrl: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sub = recent.subreddit
        if (sub == null) Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null) } else FeedIcon(sub, iconUrl)
        Spacer(Modifier.width(16.dp))
        Text(
            if (sub == null) "\"${recent.query}\"" else "\"${recent.query}\" in /r/$sub",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The front page gets Sync's white house on Reddit orange, Popular and All glyphs, communities their icon or a lettered circle. */
@Composable
private fun FeedIcon(name: String, iconUrl: String?) {
    if (name.isEmpty()) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(FrontpageOrange), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Home, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        return
    }
    val glyph: ImageVector? = when (name.lowercase()) {
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
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) {
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

/** Sync's tonal dialog button (#293040 on the dark sheet): the FAB tone at half strength. */
@Composable
private fun SearchButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(40.dp),
    ) { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
