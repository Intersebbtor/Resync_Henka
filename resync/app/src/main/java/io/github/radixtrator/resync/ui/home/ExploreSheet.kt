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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlinx.coroutines.delay

/** BROWSE opens on the subreddit list (Explore tab); SEARCH focuses the field (FAB long-press). */
enum class ExploreMode { BROWSE, SEARCH }

/**
 * Sync's Explore: a sheet to jump between feeds. Empty query shows feeds and subscriptions; typing
 * filters subscriptions, suggests communities from Reddit, and offers "Open r/…", "Open u/…" and a
 * full search.
 */
@Composable
fun ExploreSheet(container: AppContainer, mode: ExploreMode, onDismiss: () -> Unit, onOpenFeed: (String?) -> Unit) {
    val nav = LocalNavigator.current
    val subscriptions by container.subscriptions.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var subsExpanded by rememberSaveable { mutableStateOf(true) }
    var suggestions by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SearchField(
            value = query,
            onChange = { query = it },
            onSubmit = { if (q.isNotEmpty()) close { nav.push(SearchRoute(query = q)) } },
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
        )
        LazyColumn(Modifier.navigationBarsPadding().imePadding().padding(top = 8.dp)) {
            if (q.isEmpty()) {
                header("Feeds")
                item { ExploreRow("Frontpage", Icons.Outlined.Home) { onOpenFeed(null) } }
                item { ExploreRow("Popular", AppIcons.TrendingUp) { onOpenFeed("popular") } }
                item { ExploreRow("All", AppIcons.Bars) { onOpenFeed("all") } }
                if (subscriptions.isNotEmpty()) {
                    header("Subscriptions", expanded = subsExpanded) { subsExpanded = !subsExpanded }
                    if (subsExpanded) {
                        items(subscriptions.sortedBy { it.lowercase() }, key = { "sub:$it" }) { name ->
                            SubredditRow(name, iconUrl = null) { onOpenFeed(name) }
                        }
                    }
                }
            } else {
                item {
                    Button(
                        onClick = { close { nav.push(SearchRoute(query = q)) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Search all") }
                }
                val subscribed = subscriptions.filter { it.contains(q, ignoreCase = true) }.sortedBy { it.lowercase() }
                if (subscribed.isNotEmpty()) {
                    header("Subscribed")
                    items(subscribed, key = { "mine:$it" }) { name -> SubredditRow(name, iconUrl = null) { onOpenFeed(name) } }
                }
                if (suggestions.isNotEmpty()) {
                    header("Suggested subreddit results")
                    items(suggestions, key = { "suggest:${it.name}" }) { sub ->
                        SubredditRow(sub.name, sub.iconUrl, trailing = "${Format.count(sub.subscribers)} subs") { onOpenFeed(sub.name) }
                    }
                }
                header("Actions")
                item { ExploreRow("Open r/$q", AppIcons.Compass) { onOpenFeed(q) } }
                item { ExploreRow("Open u/$q", Icons.Outlined.Person) { close { nav.push(UserRoute(q)) } } }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
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
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear") }
        else Spacer(Modifier.size(48.dp))
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
private fun ExploreRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A subreddit with its round icon, or a lettered circle when Reddit has none (or we didn't fetch it). */
@Composable
private fun SubredditRow(name: String, iconUrl: String?, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (iconUrl != null) AsyncImage(iconUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
