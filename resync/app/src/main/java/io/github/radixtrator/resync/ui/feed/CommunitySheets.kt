package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.LinkFlair
import io.github.radixtrator.resync.data.RedditException
import io.github.radixtrator.resync.ui.common.FlairChip
import io.github.radixtrator.resync.ui.common.LinkCards
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.rememberRedditText
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import kotlin.coroutines.cancellation.CancellationException

/** Sync's Wiki chip: the community's wiki front page in a bottom sheet titled "pics - index". */
@Composable
fun WikiSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit) {
    val page by produceState<Result<String>?>(null, subreddit) { value = load { container.api.wikiPage(subreddit) } }
    CommunitySheet("$subreddit - index", onDismiss) {
        when (val result = page) {
            null -> Loading()
            else -> result.fold(
                onSuccess = { html ->
                    val body = rememberRedditText(html, footnotes = LocalSettings.current.linkCards)
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        RedditBodyText(body.text, MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit.Unspecified))
                        LinkCards(body.links, Modifier.padding(top = 12.dp))
                    }
                },
                onFailure = {
                    Problem(if ((it as? RedditException)?.code == 404) "r/$subreddit has no wiki" else "Couldn't load the wiki: ${it.message}")
                },
            )
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
