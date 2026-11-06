@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.CommentTree
import io.github.radixtrator.resync.data.MediaPreviewSize
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.data.VisibleNode
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.CardPreview
import io.github.radixtrator.resync.ui.common.ConfirmDeleteDialog
import io.github.radixtrator.resync.ui.common.FlairChip
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.LinkCards
import io.github.radixtrator.resync.ui.common.LinkRow
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.PostActionsSheet
import io.github.radixtrator.resync.ui.common.PostMeta
import io.github.radixtrator.resync.ui.common.PostStats
import io.github.radixtrator.resync.ui.common.PostTitle
import io.github.radixtrator.resync.ui.common.QuickAction
import io.github.radixtrator.resync.ui.common.QuickActionGrid
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.SwipeActions
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.common.hasCardPreview
import io.github.radixtrator.resync.ui.common.rememberRedditText
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.theme.ResyncColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Sync's comment page (sync-reference/SYNC_PARITY_LOG.md §3.8): one surface for toolbar, post and
// comments; colored rails for every ancestor level; tap collapses a thread, long-press opens an
// inline action bar.

@Composable
fun PostScreen(route: PostRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { PostViewModel(container, route.postId, route.commentId, route.context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val subredditIcon by vm.subredditIcon.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val visible = remember(state.comments, state.collapsed) { CommentTree.visible(state.comments, state.collapsed) }
    var postSheet by remember { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // The comment whose action bar is open (Sync shows one at a time).
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsFor by remember { mutableStateOf<Comment?>(null) }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    MessageEffect(state.message, snackbar, vm::messageShown)
    LaunchedEffect(state.postDeleted) { if (state.postDeleted) nav.pop() }
    LaunchedEffect(route.postId) { container.visited.mark(route.postId) }

    // Sync's "Remember comment position": return to last visit's spot once the comments are in,
    // and note where the user is whenever this page leaves the screen.
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.comments.isNotEmpty()) {
        if (!restored && state.comments.isNotEmpty()) {
            vm.savedPosition?.let { listState.scrollToItem(it.index, it.offset) }
            restored = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { vm.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    fun reply(target: String, quote: String) {
        if (vm.requireLogin()) nav.push(ComposeRoute(ComposeMode.REPLY, target = target, quote = quote))
    }

    // LazyColumn rows: post header, optional single-thread banner, then the comments.
    val firstCommentRow = 1 + if (vm.focusCommentId != null) 1 else 0
    val topLevelRows = remember(visible, firstCommentRow) {
        visible.indices.filter { visible[it].node.depth == 0 }.map { it + firstCommentRow }
    }
    fun scrollToComment(fullname: String) {
        val index = visible.indexOfFirst { (it.node as? Comment)?.fullname == fullname }
        scope.launch { listState.animateScrollToItem(if (index < 0) 0 else index + firstCommentRow) }
    }

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surface),
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        state.post?.let { post ->
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.push(FeedRoute(post.subreddit)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SubredditIcon(post.subreddit, subredditIcon, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(post.subreddit, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(state.sort.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Outlined.Search, "Search comments") }
                        IconButton(onClick = { sortOpen = true }) { Icon(AppIcons.Sort, "Sort comments") }
                        IconButton(onClick = { actionsOpen = true }) { Icon(Icons.Default.MoreVert, "Comment actions") }
                    },
                )
            },
            floatingActionButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    if (LocalSettings.current.commentNavigation && topLevelRows.size > 1) {
                        SmallFloatingActionButton(onClick = {
                            val target = topLevelRows.lastOrNull { it < listState.firstVisibleItemIndex } ?: 0
                            scope.launch { listState.animateScrollToItem(target) }
                        }) { Icon(Icons.Default.KeyboardArrowUp, "Previous comment") }
                        SmallFloatingActionButton(onClick = {
                            topLevelRows.firstOrNull { it > listState.firstVisibleItemIndex }
                                ?.let { scope.launch { listState.animateScrollToItem(it) } }
                        }) { Icon(Icons.Default.KeyboardArrowDown, "Next comment") }
                    }
                    state.post?.let { post ->
                        Surface(
                            onClick = { reply(post.fullname, post.title) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Reply, "Reply to post") }
                        }
                    }
                }
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.load(refresh = true) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                // Not a list item: a lone keyed status row would stay pinned in view once content arrives.
                if (state.post == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.loading) {
                            CircularProgressIndicator()
                        } else if (state.error != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                TextButton(onClick = { vm.load(refresh = false) }) { Text("Retry") }
                            }
                        }
                    }
                    return@PullToRefreshBox
                }
                val post = state.post!!
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                    item(key = "post") {
                        PostHeader(post = post, onVote = vm::votePost, onToggleSave = vm::toggleSavePost, onMore = { postSheet = true })
                    }
                    if (vm.focusCommentId != null) {
                        item(key = "focus") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Viewing a single thread", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { nav.replaceTop(PostRoute(post.id)) }) { Text("View all comments") }
                            }
                        }
                    }
                    items(visible, key = { (if (it.node is Comment) "c" else "m") + it.node.id }) { row ->
                        // Sync separates top-level threads with a band of the window color.
                        val separated = row.node.depth == 0 && row !== visible.firstOrNull()
                        when (val node = row.node) {
                            is Comment -> CommentRow(
                                comment = node,
                                collapsed = node.id in state.collapsed,
                                hiddenCount = row.hiddenCount,
                                separated = separated,
                                selected = selected == node.id,
                                onToggle = {
                                    selected = null
                                    vm.toggleCollapsed(node)
                                },
                                onSelect = { selected = if (selected == node.id) null else node.id },
                                onVote = { vm.voteComment(node, it) },
                                onToggleSave = { vm.toggleSaveComment(node) },
                                onReply = { reply(node.fullname, node.body) },
                                onParent = { scrollToComment(node.parentId) },
                                onMore = { optionsFor = node },
                                onShare = { nav.share("https://www.reddit.com${post.permalink}${node.id}/", post.title) },
                            )
                            is MoreComments -> MoreRow(
                                more = node,
                                separated = separated,
                                loading = node.id in state.loadingMore,
                                onClick = {
                                    if (node.children.isNotEmpty()) {
                                        vm.loadMore(node)
                                    } else {
                                        // "Continue this thread": a new page rooted at the parent comment.
                                        nav.push(PostRoute(post.id, node.parentId.removePrefix("t1_")))
                                    }
                                },
                            )
                        }
                    }
                    item(key = "status") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> CircularProgressIndicator()
                                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                    TextButton(onClick = { vm.load(refresh = false) }) { Text("Retry") }
                                }
                                state.comments.isEmpty() -> Text("No comments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    val post = state.post
    if (postSheet && post != null) {
        PostActionsSheet(post = post, onDismiss = { postSheet = false }, onHide = null, onDelete = { vm.delete(post.fullname) })
    }
    if (sortOpen) CommentSortSheet(state.sort, onDismiss = { sortOpen = false }, onPick = vm::setSort)
    if (actionsOpen && post != null) {
        TopSheet(onDismiss = { actionsOpen = false }) {
            SheetTitle("Comment actions")
            QuickActionGrid(
                listOf(
                    QuickAction("Reply", AppIcons.Reply) { reply(post.fullname, post.title) },
                    QuickAction("Refresh", Icons.Outlined.Refresh) { vm.load(refresh = true) },
                    QuickAction("Collapse all", AppIcons.CollapseAll, onClick = vm::collapseAll),
                    QuickAction("Settings", Icons.Outlined.Settings) { nav.push(SettingsRoute) },
                    QuickAction("Sort", AppIcons.Sort) { sortOpen = true },
                    QuickAction("Search", Icons.Outlined.Search) { searchOpen = true },
                ),
                onPicked = { actionsOpen = false },
            )
        }
    }
    if (searchOpen) {
        CommentSearchSheet(
            rows = visible,
            onDismiss = { searchOpen = false },
            onPick = { comment ->
                searchOpen = false
                scrollToComment(comment.fullname)
            },
        )
    }
    optionsFor?.let { comment ->
        if (post != null) {
            CommentOptionsSheet(
                comment = comment,
                post = post,
                onDismiss = { optionsFor = null },
                onDelete = { vm.delete(comment.fullname) },
            )
        }
    }
}

// --- Post header ---

@Composable
private fun PostHeader(post: Post, onVote: (Int) -> Unit, onToggleSave: () -> Unit, onMore: () -> Unit) {
    val nav = LocalNavigator.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        if (post.hasCardPreview()) {
            // Sync's default "Cropped height": a banner over the comments that still opens the media.
            val cropped = LocalSettings.current.postMediaPreview == MediaPreviewSize.CROPPED
            CardPreview(post, onClick = { nav.openMedia(post) }, maxHeight = if (cropped) 180.dp else null)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            // Sync's 16sp title at its "Smaller" base font size; Sync draws it in Product Sans, which can't be bundled.
            PostTitle(post, MaterialTheme.typography.bodyLarge.copy(fontSize = 14.4.sp, lineHeight = TextUnit.Unspecified), dimWhenRead = false)
            Spacer(Modifier.height(4.dp))
            PostMeta(post, showSubreddit = false, maxLines = 3, suffix = post.upvoteRatio?.let { "${(it * 100).roundToInt()}% upvoted" })
            if (post.flair != null) {
                Spacer(Modifier.height(8.dp))
                FlairChip(post)
            }
            if (post.media is PostMedia.Link && !post.hasCardPreview()) {
                Spacer(Modifier.height(10.dp))
                LinkRow(post, onClick = { nav.openMedia(post) })
            }
            post.selfTextHtml?.takeIf { it.isNotBlank() }?.let { html ->
                val body = rememberRedditText(html, footnotes = LocalSettings.current.linkCards)
                Spacer(Modifier.height(10.dp))
                RedditBodyText(body.text, CommentBody)
                LinkCards(body.links, Modifier.padding(top = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PostStats(post, Modifier.weight(1f))
            IconButton(onClick = { onVote(if (post.likes == true) 0 else 1) }) {
                Icon(AppIcons.ArrowUp, "Upvote", tint = if (post.likes == true) ResyncColors.Upvote else muted)
            }
            IconButton(onClick = { onVote(if (post.likes == false) 0 else -1) }) {
                Icon(AppIcons.ArrowDown, "Downvote", tint = if (post.likes == false) ResyncColors.Downvote else muted)
            }
            IconButton(onClick = onToggleSave) {
                Icon(
                    if (post.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                    if (post.saved) "Unsave" else "Save",
                    tint = if (post.saved) ResyncColors.Saved else muted,
                )
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Post options", tint = muted) }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
    }
}

// --- Comments ---

/** Indent per depth; rails sit at each multiple of it. Deeper replies stop indenting at [MAX_DEPTH]. */
private val INDENT = 16.dp
private const val MAX_DEPTH = 10

/**
 * Selftext and comment bodies: Sync's 14sp at its default "Smaller" base font size (0.9x), measured from
 * sync-reference 23 and 55. The font's own line spacing, like Sync, so lines holding a larger link grow.
 */
private val CommentBody: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.6.sp, lineHeight = TextUnit.Unspecified)

private fun railColor(level: Int) = ResyncColors.Depth[(level - 1) % ResyncColors.Depth.size]

/** Draws a rail for every ancestor level 1..[depth], so threads read as continuous colored lines. */
private fun Modifier.rails(depth: Int) = drawBehind {
    val width = 2.dp.toPx()
    for (level in 1..depth) {
        val x = (INDENT * level).toPx()
        drawRect(railColor(level), topLeft = Offset(x, 0f), size = Size(width, size.height))
    }
}

private fun contentStart(depth: Int): Dp = INDENT * (depth + 1)

@Composable
private fun CommentRow(
    comment: Comment,
    collapsed: Boolean,
    hiddenCount: Int,
    separated: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onReply: () -> Unit,
    onParent: () -> Unit,
    onMore: () -> Unit,
    onShare: () -> Unit,
) {
    val depth = comment.depth.coerceAtMost(MAX_DEPTH)
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    Column {
        if (separated) Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        SwipeActions(
            stages = settings.commentSwipeStages,
            enabled = settings.commentSwipe,
            onAction = { action ->
                when (action) {
                    SwipeAction.UPVOTE -> onVote(if (comment.likes == true) 0 else 1)
                    SwipeAction.DOWNVOTE -> onVote(if (comment.likes == false) 0 else -1)
                    SwipeAction.SAVE -> onToggleSave()
                    SwipeAction.PROFILE -> if (comment.author != "[deleted]") nav.push(UserRoute(comment.author))
                    SwipeAction.SHARE -> onShare()
                    SwipeAction.REPLY -> onReply()
                    SwipeAction.HIDE, SwipeAction.MARK_READ -> Unit
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Opaque, so the swipe panel only shows where the row has moved away.
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)
                    .combinedClickable(onClick = onToggle, onLongClick = onSelect)
                    .rails(depth)
                    .padding(start = contentStart(depth), end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                CommentHeader(comment, collapsed, hiddenCount)
                // Collapsing a reply chain keeps the comment itself readable (Sync's default); a lone
                // comment folds to its header.
                if (!collapsed || hiddenCount > 0) {
                    val body = rememberRedditText(comment.bodyHtml, footnotes = settings.linkCards)
                    Spacer(Modifier.height(6.dp))
                    RedditBodyText(body.text, CommentBody)
                    LinkCards(body.links, Modifier.padding(top = 10.dp))
                }
            }
        }
        if (selected) {
            CommentActionBar(
                comment = comment,
                depth = depth,
                onVote = onVote,
                onToggleSave = onToggleSave,
                onReply = onReply,
                onParent = onParent,
                onMore = onMore,
            )
        }
    }
}

/** "avatar username [flair] 859 points • 4h", OP in a tinted pill, mods in green, "+N" when folded. */
@Composable
private fun CommentHeader(comment: Comment, collapsed: Boolean, hiddenCount: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nameColor = when (comment.distinguished) {
        "moderator" -> ResyncColors.Moderator
        "admin" -> ResyncColors.Nsfw
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }
    val scoreColor = when (comment.likes) {
        true -> ResyncColors.Upvote
        false -> ResyncColors.Downvote
        null -> muted
    }
    val opBackground = MaterialTheme.colorScheme.secondaryContainer
    val flairBackground = MaterialTheme.colorScheme.surfaceVariant
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = nameColor, background = if (comment.isSubmitter) opBackground else Color.Unspecified)) {
            append(if (comment.isSubmitter) " ${comment.author} " else comment.author)
        }
        if (comment.isSubmitter) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(" OP") }
        if (comment.distinguished == "moderator") withStyle(SpanStyle(color = ResyncColors.Moderator, fontWeight = FontWeight.Bold)) { append(" MOD") }
        if (comment.stickied) withStyle(SpanStyle(color = ResyncColors.Moderator)) { append(" pinned") }
        comment.flair?.let {
            append(" ")
            withStyle(SpanStyle(background = flairBackground, color = muted)) { append(" $it ") }
        }
        append(" ")
        withStyle(SpanStyle(color = scoreColor)) {
            append(if (comment.scoreHidden) "[score hidden]" else if (comment.score == 1) "1 point" else "${Format.count(comment.score)} points")
        }
        withStyle(SpanStyle(color = muted)) {
            append(" • ${Format.timeAgo(comment.createdUtc)}")
            if (comment.edited) append("*")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(comment.author, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (collapsed && hiddenCount > 0) {
            Text(
                "+$hiddenCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Sync's long-press bar: upvote, downvote, save, profile, reply, go to parent, more. */
@Composable
private fun CommentActionBar(
    comment: Comment,
    depth: Int,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onReply: () -> Unit,
    onParent: () -> Unit,
    onMore: () -> Unit,
) {
    val nav = LocalNavigator.current
    val normal = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .rails(depth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(AppIcons.ArrowUp, "Upvote", if (comment.likes == true) ResyncColors.Upvote else normal) { onVote(if (comment.likes == true) 0 else 1) }
        BarButton(AppIcons.ArrowDown, "Downvote", if (comment.likes == false) ResyncColors.Downvote else normal) { onVote(if (comment.likes == false) 0 else -1) }
        BarButton(
            if (comment.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            if (comment.saved) "Unsave" else "Save",
            if (comment.saved) ResyncColors.Saved else normal,
            onToggleSave,
        )
        BarButton(Icons.Outlined.Person, "Profile", normal) { if (comment.author != "[deleted]") nav.push(UserRoute(comment.author)) }
        BarButton(AppIcons.Reply, "Reply", normal, onReply)
        BarButton(Icons.Default.KeyboardArrowUp, "Go to parent", normal, onParent)
        BarButton(Icons.Default.MoreVert, "Comment options", normal, onMore)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint)
    }
}

@Composable
private fun MoreRow(more: MoreComments, separated: Boolean, loading: Boolean, onClick: () -> Unit) {
    val depth = more.depth.coerceAtMost(MAX_DEPTH)
    Column {
        if (separated) Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .rails(depth)
                .padding(start = contentStart(depth), end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (more.children.isEmpty()) "Continue this thread" else "View more (${more.count})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (loading) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** A colored initial: comment listings don't carry avatars, and fetching one per author is too costly. */
@Composable
private fun UserAvatar(name: String, size: Dp) {
    val color = ResyncColors.Depth[name.hashCode().absoluteValue % ResyncColors.Depth.size]
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SubredditIcon(name: String, url: String?, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

// --- Sheets ---

/** Sync lists comment sorts in this order. */
private val SyncCommentSorts = listOf(CommentSort.TOP, CommentSort.BEST, CommentSort.NEW, CommentSort.OLD, CommentSort.CONTROVERSIAL, CommentSort.QA)

private fun commentSortIcon(sort: CommentSort): ImageVector = when (sort) {
    CommentSort.TOP -> AppIcons.Bars
    CommentSort.BEST -> Icons.Outlined.Star
    CommentSort.NEW -> AppIcons.Burst
    CommentSort.OLD -> AppIcons.Clock
    CommentSort.CONTROVERSIAL -> AppIcons.TrendingDown
    CommentSort.QA -> AppIcons.Comment
}

@Composable
private fun CommentSortSheet(current: CommentSort, onDismiss: () -> Unit, onPick: (CommentSort) -> Unit) {
    TopSheet(onDismiss = onDismiss) {
        SheetTitle("Comment sort")
        SyncCommentSorts.forEach { sort ->
            OptionRow(sort.label, commentSortIcon(sort), selected = sort == current, onClick = {
                onDismiss()
                onPick(sort)
            })
        }
    }
}

/** Sync's "Comment options": share, open, select or copy text, plus edit/delete on your own. */
@Composable
private fun CommentOptionsSheet(comment: Comment, post: Post, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val isOwn = LocalAccount.current?.equals(comment.author, ignoreCase = true) == true
    val permalink = "https://www.reddit.com${post.permalink}${comment.id}/"
    var copying by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (copying) {
                SheetTitle("Copy", Modifier.padding(top = 0.dp))
                OptionRow("Copy text", AppIcons.Text, onClick = { act { clipboard.setText(AnnotatedString(comment.body)) } })
                OptionRow("Copy permalink", AppIcons.Link, onClick = { act { clipboard.setText(AnnotatedString(permalink)) } })
                OptionRow("Copy username", Icons.Outlined.Person, onClick = { act { clipboard.setText(AnnotatedString(comment.author)) } })
            } else {
                SheetTitle("Comment options", Modifier.padding(top = 0.dp))
                OptionRow("Share", Icons.Outlined.Share, onClick = { act { nav.share(permalink, post.title) } })
                OptionRow("Open in browser", AppIcons.OpenExternal, onClick = { act { nav.openExternal(permalink) } })
                OptionRow("Select text", AppIcons.Text, onClick = { selecting = true })
                OptionRow("Copy...", AppIcons.Copy, onClick = { copying = true })
                if (isOwn) {
                    OptionRow("Edit", Icons.Outlined.Edit, onClick = {
                        act { nav.push(ComposeRoute(ComposeMode.EDIT, target = comment.fullname, quote = post.title, initial = comment.body)) }
                    })
                    OptionRow("Delete", Icons.Outlined.Delete, onClick = { confirmDelete = true })
                }
            }
        }
    }
    if (selecting) {
        AlertDialog(
            onDismissRequest = { selecting = false },
            title = { Text("Select text") },
            text = { SelectionContainer { Text(comment.body, style = CommentBody) } },
            confirmButton = { TextButton(onClick = { selecting = false; onDismiss() }) { Text("Done") } },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog("comment", onConfirm = { confirmDelete = false; act(onDelete) }, onDismiss = { confirmDelete = false })
    }
}

/** Sync's in-thread search: a sheet that filters the loaded comments; picking one scrolls to it. */
@Composable
private fun CommentSearchSheet(rows: List<VisibleNode>, onDismiss: () -> Unit, onPick: (Comment) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val q = query.trim()
    val matches = remember(rows, q) {
        if (q.length < 2) emptyList()
        else rows.mapNotNull { it.node as? Comment }.filter { it.body.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search comments", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 8.dp)) {
            if (q.length >= 2 && matches.isEmpty()) {
                item { Text("No matching comments", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(matches, key = { it.id }) { comment ->
                Column(Modifier.fillMaxWidth().clickable { onPick(comment) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(comment.author, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(comment.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
