@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.ui.common.HeaderChip
import io.github.radixtrator.resync.ui.common.LargeHeader
import io.github.radixtrator.resync.ui.common.PostActions
import io.github.radixtrator.resync.ui.common.PostLayoutItem
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.gutter
import io.github.radixtrator.resync.ui.common.spacing
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.theme.FullSizeText

/**
 * Sync's Customize view (Settings > View type, also the Change view sheet's "Customize view" button): a large
 * "View type" title, a chip per layout in Sync's order (Slides … List), a live preview of example posts in the
 * chosen layout, then that layout's options. Changes apply to every feed using the layout at once; the toolbar's
 * reset icon puts the chosen layout back to its defaults. Sync's font pickers and button toggles aren't here yet.
 */
@Composable
fun CustomizeViewScreen(initial: PostLayout, container: AppContainer) {
    val nav = LocalNavigator.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var layout by rememberSaveable { mutableStateOf(initial) }
    val options = settings.viewOptionsFor(layout)
    val customized = layout in settings.viewOptions

    fun change(option: ViewOption, value: Boolean) = container.settings.update { s ->
        s.copy(viewOptions = s.viewOptions + (layout to option.write(s.viewOptionsFor(layout), value)))
    }

    SwipeBack(enabled = settings.swipeBack, onBack = nav::pop) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {},
                    actions = {
                        IconButton(
                            onClick = { container.settings.update { it.copy(viewOptions = it.viewOptions - layout) } },
                            enabled = customized,
                        ) { Icon(Icons.Outlined.Refresh, "Reset ${layout.label} to defaults") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                // Sync's chip row runs from the richest layout to the plainest.
                LargeHeader(title = "View type") {
                    PostLayout.entries.reversed().forEach { option ->
                        HeaderChip(
                            label = option.label,
                            icon = null,
                            onClick = { layout = option },
                            container = if (option == layout) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                    }
                }
                // The preview is drawn at feed size (with the feed's base font scale), so it looks as it will.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = layout.gutter, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.spacing),
                ) {
                    SamplePosts.forEach { post -> PostLayoutItem(layout, post, showSubreddit = true, actions = NoActions) }
                }
                FullSizeText {
                    Column(Modifier.padding(bottom = 16.dp)) {
                        sectionsFor(layout).forEach { (title, rows) ->
                            Section(title)
                            rows.forEach { option ->
                                Toggle(option.title, option.summary, option.read(options)) { v -> change(option, v) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One switch on a View type screen, with the catalogue's title and the [ViewOptions] field it flips. */
private enum class ViewOption(
    val title: String,
    val summary: String?,
    val read: (ViewOptions) -> Boolean,
    val write: (ViewOptions, Boolean) -> ViewOptions,
) {
    IMAGE_PREVIEWS("Show image previews", null, { it.imagePreviews }, { o, v -> o.copy(imagePreviews = v) }),
    SELFTEXT_PREVIEWS("Show selftext previews", null, { it.selftextPreviews }, { o, v -> o.copy(selftextPreviews = v) }),
    SUBREDDIT_ICONS("Show subreddit icons", "Shown on Frontpage, All, search etc", { it.subredditIcons }, { o, v -> o.copy(subredditIcons = v) }),
    SELFTEXT_BORDER("Show a border around selftext", null, { it.selftextBorder }, { o, v -> o.copy(selftextBorder = v) }),
    IMAGE_ABOVE_TITLE("Image above title", null, { it.imageAboveTitle }, { o, v -> o.copy(imageAboveTitle = v) }),
    THUMBNAIL_LEFT("Align thumbnail left", null, { it.thumbnailLeft }, { o, v -> o.copy(thumbnailLeft = v) }),
    THUMBNAILS("Thumbnails", null, { it.thumbnails }, { o, v -> o.copy(thumbnails = v) }),
    TYPE_INDICATORS("Type indicators", null, { it.typeIndicators }, { o, v -> o.copy(typeIndicators = v) }),
    SINGLE_LINE_TITLE("Limit title to a single line", null, { it.singleLineTitle }, { o, v -> o.copy(singleLineTitle = v) }),
}

/** Each layout's options under Sync's section names (sync-reference/settings-catalogue.md, "View type"). */
private fun sectionsFor(layout: PostLayout): List<Pair<String, List<ViewOption>>> = when (layout) {
    PostLayout.SLIDES -> listOf(
        "Preview options" to listOf(ViewOption.IMAGE_PREVIEWS, ViewOption.SELFTEXT_PREVIEWS, ViewOption.SUBREDDIT_ICONS),
        "Experimental options" to listOf(ViewOption.SELFTEXT_BORDER),
    )
    PostLayout.CARD -> listOf(
        "Image options" to listOf(ViewOption.IMAGE_ABOVE_TITLE),
        "Selftext options" to listOf(ViewOption.SELFTEXT_PREVIEWS),
    )
    PostLayout.SMALL_CARDS -> listOf("Image options" to listOf(ViewOption.THUMBNAIL_LEFT))
    PostLayout.SMALLER_CARDS -> listOf(
        "Image options" to listOf(ViewOption.TYPE_INDICATORS),
        "Fonts and sizing" to listOf(ViewOption.SINGLE_LINE_TITLE),
    )
    PostLayout.COMPACT, PostLayout.LIST -> listOf(
        "Image options" to listOf(ViewOption.THUMBNAILS, ViewOption.TYPE_INDICATORS),
        "Fonts and sizing" to listOf(ViewOption.SINGLE_LINE_TITLE),
    )
}

/** The preview only shows; taps go nowhere. */
private val NoActions = PostActions(onOpen = {}, onMedia = {}, onMore = {}, onVote = {}, onToggleSave = {})

// A Wikimedia Commons photo of a golden retriever, standing in for Sync's dog photo.
private const val SAMPLE_IMAGE = "https://upload.wikimedia.org/wikipedia/commons/6/6e/Golde33443.jpg"

/**
 * Sync's "Example post" by ljdawson (an upvoted, saved photo at 9999 points), plus a text post so the selftext
 * options show too. Ages are fixed offsets from now.
 */
private val SamplePosts: List<Post>
    get() {
        val now = System.currentTimeMillis() / 1000
        return listOf(
            Post(
                id = "resync_example_image",
                fullname = "t3_resync_example_image",
                subreddit = "pics",
                author = "ljdawson",
                title = "Example post",
                selfText = "",
                selfTextHtml = null,
                url = SAMPLE_IMAGE,
                domain = "upload.wikimedia.org",
                permalink = "/r/pics/comments/resync_example_image/example_post/",
                score = 9999,
                numComments = 154,
                createdUtc = now - 4 * 3600,
                over18 = false,
                spoiler = false,
                stickied = false,
                isSelf = false,
                likes = true,
                saved = true,
                flair = "Example",
                media = PostMedia.Image(ImageSource(SAMPLE_IMAGE, 1600, 1200), SAMPLE_IMAGE, isGif = false),
            ),
            Post(
                id = "resync_example_text",
                fullname = "t3_resync_example_text",
                subreddit = "resync",
                author = "ljdawson",
                title = "Example text post",
                selfText = "This is how a text post's preview looks in this view.\n\nThe rest of the post stays in the thread.",
                selfTextHtml = null,
                url = "https://www.reddit.com/r/resync/comments/resync_example_text/example_text_post/",
                domain = "self.resync",
                permalink = "/r/resync/comments/resync_example_text/example_text_post/",
                score = 42,
                numComments = 7,
                createdUtc = now - 26 * 3600,
                over18 = false,
                spoiler = false,
                stickied = false,
                isSelf = true,
                likes = null,
                saved = false,
                flair = null,
                media = PostMedia.None,
            ),
        )
    }
