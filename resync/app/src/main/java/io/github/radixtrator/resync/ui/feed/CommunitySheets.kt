package io.github.radixtrator.resync.ui.feed

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.data.Multi
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLayoutResult
import io.github.radixtrator.resync.data.Wiki
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.LinkFlair
import io.github.radixtrator.resync.data.RedditException
import io.github.radixtrator.resync.data.SubredditRule
import io.github.radixtrator.resync.notify.Notifications
import io.github.radixtrator.resync.ui.common.FlairChip
import io.github.radixtrator.resync.ui.common.LinkCards
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.common.rememberRedditText
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.ModRoute
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import kotlin.coroutines.cancellation.CancellationException

/**
 * The sheet behind a header chip or an About ⋮ item: wiki, flairs, rules or moderators. Picking something
 * that leaves for another page dismisses the sheet and hands the route to [onNavigate].
 */
@Composable
fun CommunityActionSheet(container: AppContainer, community: String, action: AboutAction, onDismiss: () -> Unit, onNavigate: (NavKey) -> Unit) {
    val leave = { route: NavKey ->
        onDismiss()
        onNavigate(route)
    }
    when (action) {
        AboutAction.WIKI -> WikiSheet(container, community, onDismiss)
        AboutAction.FLAIRS -> FlairsSheet(container, community, onPick = { leave(SearchRoute("flair_name:\"${it.text}\"", subreddit = community)) }, onDismiss)
        AboutAction.RULES -> RulesSheet(container, community, onDismiss)
        AboutAction.ADD_TO_MULTI -> AddToMultiSheet(container, community, onDismiss)
        AboutAction.MOD_TOOLS -> leave(ModRoute(community))
        AboutAction.MODS -> ModeratorsSheet(container, community, onOpen = { leave(UserRoute(it)) }, onDismiss)
        AboutAction.SHARE, AboutAction.MESSAGE_MODS -> Unit
    }
}

/**
 * Sync's "Add to multi": your custom feeds, with a check on the ones that already hold [subreddit]; tapping one
 * adds or removes it there.
 */
@Composable
fun AddToMultiSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit) {
    var multis by remember { mutableStateOf<Result<List<Multi>>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { multis = load { container.api.multis() } }
    CommunitySheet("Add r/$subreddit to a multi", onDismiss) {
        when (val result = multis) {
            null -> Loading()
            else -> result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) Problem("You have no custom feeds yet. Create one on reddit.com, then add communities here.")
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        list.forEach { multi ->
                            val has = subreddit.lowercase() in multi.subreddits.map { it.lowercase() }
                            OptionRow(multi.name, AppIcons.Gallery, selected = has, onClick = {
                                scope.launch {
                                    load {
                                        if (has) container.api.removeFromMulti(multi.path, subreddit) else container.api.addToMulti(multi.path, subreddit)
                                    }.fold(
                                        onSuccess = {
                                            Toast.makeText(context, if (has) "Removed from ${multi.name}" else "Added to ${multi.name}", Toast.LENGTH_SHORT).show()
                                            multis = load { container.api.multis() }
                                        },
                                        onFailure = { Toast.makeText(context, it.message ?: "Couldn't change the feed", Toast.LENGTH_SHORT).show() },
                                    )
                                }
                            })
                        }
                    }
                },
                onFailure = { Problem("Couldn't load your custom feeds: ${it.message}") },
            )
        }
    }
}

/** Runs the About ⋮ items that need no sheet (Share, Message mods); false for the ones that do. */
fun Navigator.runAboutAction(action: AboutAction, community: String): Boolean = when (action) {
    AboutAction.SHARE -> {
        share("https://www.reddit.com/r/$community", "r/$community")
        true
    }
    // A message to "/r/name" goes to the community's moderators.
    AboutAction.MESSAGE_MODS -> {
        push(ComposeRoute(ComposeMode.MESSAGE, to = "/r/$community"))
        true
    }
    else -> false
}

/**
 * Sync's Watch, for a chip: stops watching, or asks for notification access first if needed and then
 * watches. [onMessage] reports what happened.
 */
@Composable
fun rememberWatchToggle(container: AppContainer, onMessage: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    fun watch(name: String) {
        container.setWatching(name, true)
        onMessage("Watching r/$name: you'll get a notification for each new post")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val name = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (granted) watch(name) else onMessage("Allow notifications to watch communities")
    }
    return { name ->
        when {
            container.watch.isWatching(name) -> {
                container.setWatching(name, false)
                onMessage("Stopped watching r/$name")
            }
            Notifications.canNotify(context) -> watch(name)
            else -> {
                pending = name
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Sync's Wiki chip: the community's wiki in a bottom sheet titled "pics - index". Links to other pages of the
 * same wiki open inside the sheet (Back returns), the page list and a table of contents of the page's headings
 * sit on top, and tapping a heading scrolls to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WikiSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit) {
    var history by remember(subreddit) { mutableStateOf(listOf("index")) }
    val pageName = history.last()
    val page by produceState<Result<String>?>(null, subreddit, pageName) {
        value = null
        value = load { container.api.wikiPage(subreddit, pageName) }
    }
    val pages by produceState<List<String>>(emptyList(), subreddit) { value = load { container.api.wikiPages(subreddit) }.getOrDefault(emptyList()) }
    val outer = LocalUriHandler.current
    val wikiLinks = remember(outer, subreddit) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val link = Wiki.parseLink(uri)
                if (link != null && link.subreddit.equals(subreddit, ignoreCase = true)) history = history + link.page else outer.openUri(uri)
            }
        }
    }
    CommunitySheet("$subreddit - $pageName", onDismiss) {
        CompositionLocalProvider(LocalUriHandler provides wikiLinks) {
            when (val result = page) {
                null -> Loading()
                else -> result.fold(
                    onSuccess = { html ->
                        val body = rememberRedditText(html, footnotes = LocalSettings.current.linkCards)
                        val headings = remember(html) { Wiki.headings(html) }
                        val scroll = rememberScrollState()
                        val scope = rememberCoroutineScope()
                        var layout by remember(html) { mutableStateOf<TextLayoutResult?>(null) }
                        var textTop by remember(html) { mutableStateOf(0) }
                        Column(Modifier.verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            if (history.size > 1) {
                                TextButton(onClick = { history = history.dropLast(1) }) { Text("Back to ${history[history.size - 2]}") }
                            }
                            val others = pages.filter { it != pageName }
                            if (others.isNotEmpty()) {
                                Text("Pages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                                    others.forEach { name -> AssistChip(onClick = { history = history + name }, label = { Text(name) }) }
                                }
                            }
                            if (headings.size > 1) {
                                Text("Contents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                headings.forEach { h ->
                                    Text(
                                        h.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                val l = layout ?: return@clickable
                                                val at = body.text.text.indexOf(h.text)
                                                if (at >= 0) scope.launch { scroll.animateScrollTo(textTop + l.getLineTop(l.getLineForOffset(at)).toInt()) }
                                            }
                                            .padding(start = ((h.level - 1).coerceAtMost(3) * 12).dp, top = 6.dp, bottom = 6.dp),
                                    )
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            Column(Modifier.onGloballyPositioned { textTop = it.positionInParent().y.toInt() }) {
                                RedditBodyText(
                                    body.text,
                                    MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified),
                                    onLayout = { layout = it },
                                )
                                LinkCards(body.links, Modifier.padding(top = 12.dp))
                            }
                        }
                    },
                    onFailure = {
                        Problem(if ((it as? RedditException)?.code == 404) "r/$subreddit has no wiki page \"$pageName\"" else "Couldn't load the wiki: ${it.message}")
                    },
                )
            }
        }
    }
}

/**
 * The Flairs chip: the community's post flairs as chips. Sync's version only errors out ("Error loading post
 * flairs"); picking one here searches the community for that flair, which is how Reddit filters by flair.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlairsSheet(container: AppContainer, subreddit: String, onPick: (LinkFlair) -> Unit, onDismiss: () -> Unit) {
    val flairs by produceState<Result<List<LinkFlair>>?>(null, subreddit) { value = load { container.api.linkFlairs(subreddit) } }
    CommunitySheet("Post flairs", onDismiss) {
        when (val result = flairs) {
            null -> Loading()
            else -> result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        Problem("r/$subreddit has no post flairs")
                    } else {
                        FlowRow(
                            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            list.forEach { flair -> FlairChip(flair.text, flair.color, Modifier.clickable { onPick(flair) }) }
                        }
                    }
                },
                onFailure = { Problem("Couldn't load post flairs: ${it.message}") },
            )
        }
    }
}

/** The About sheet's "View rules": the community's rules, numbered, each with its explanation. */
@Composable
fun RulesSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit) {
    val rules by produceState<Result<List<SubredditRule>>?>(null, subreddit) { value = load { container.api.rules(subreddit) } }
    CommunitySheet("Rules", onDismiss) {
        when (val result = rules) {
            null -> Loading()
            else -> result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        Problem("r/$subreddit has no rules listed")
                    } else {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            list.forEachIndexed { i, rule ->
                                Column {
                                    Text("${i + 1}. ${rule.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    rule.descriptionHtml?.let { html ->
                                        RedditBodyText(
                                            rememberRedditHtml(html),
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = TextUnit.Unspecified,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onFailure = { Problem("Couldn't load the rules: ${it.message}") },
            )
        }
    }
}

/** The About sheet's "View mods": the moderators, each opening their profile. */
@Composable
fun ModeratorsSheet(container: AppContainer, subreddit: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val mods by produceState<Result<List<String>>?>(null, subreddit) { value = load { container.api.moderators(subreddit) } }
    CommunitySheet("Moderators", onDismiss) {
        when (val result = mods) {
            null -> Loading()
            else -> result.fold(
                onSuccess = { names ->
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                        names.forEach { name -> OptionRow("u/$name", Icons.Outlined.Person, onClick = { onOpen(name) }) }
                    }
                },
                onFailure = { Problem("Couldn't load the moderators: ${it.message}") },
            )
        }
    }
}

private suspend fun <T> load(block: suspend () -> T): Result<T> =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunitySheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            SheetTitle(title, topPadding = 0.dp)
            content()
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Problem(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    )
}
