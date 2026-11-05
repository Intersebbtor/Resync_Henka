@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.post

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.CommentTree
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.ConfirmDeleteDialog
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.LinkRow
import io.github.radixtrator.resync.ui.common.MediaPreview
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.MetaLine
import io.github.radixtrator.resync.ui.common.PostActionsSheet
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.VoteBar
import io.github.radixtrator.resync.ui.common.rememberRedditHtml
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.theme.ResyncColors
import kotlinx.coroutines.launch

@Composable
fun PostScreen(route: PostRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { PostViewModel(container, route.postId, route.commentId, route.context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visible = remember(state.comments, state.collapsed) { CommentTree.visible(state.comments, state.collapsed) }
    var postSheet by remember { mutableStateOf(false) }

    MessageEffect(state.message, snackbar, vm::messageShown)
    LaunchedEffect(state.postDeleted) { if (state.postDeleted) nav.pop() }

    fun reply(target: String, quote: String) {
        if (vm.requireLogin()) nav.push(ComposeRoute(ComposeMode.REPLY, target = target, quote = quote))
    }

    // LazyColumn rows: post header, optional single-thread banner, then the comments.
    val firstCommentRow = 1 + if (vm.focusCommentId != null) 1 else 0
    val topLevelRows = remember(visible, firstCommentRow) {
        visible.indices.filter { visible[it].node.depth == 0 }.map { it + firstCommentRow }
    }

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column {
                            Text(state.post?.let { "r/${it.subreddit}" } ?: "", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${state.post?.numComments?.let(Format::count) ?: "…"} comments · ${state.sort.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        state.post?.let { post ->
                            IconButton(onClick = { reply(post.fullname, post.title) }) { Icon(AppIcons.Reply, "Reply") }
                        }
                        CommentSortMenu(onPick = vm::setSort)
                        if (state.post != null) IconButton(onClick = { postSheet = true }) { Icon(Icons.Default.MoreVert, "More") }
                    },
                )
            },
            floatingActionButton = {
                if (topLevelRows.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.navigationBarsPadding()) {
                        SmallFloatingActionButton(onClick = {
                            val target = topLevelRows.lastOrNull { it < listState.firstVisibleItemIndex } ?: 0
                            scope.launch { listState.animateScrollToItem(target) }
                        }) { Icon(Icons.Default.KeyboardArrowUp, "Previous comment") }
                        SmallFloatingActionButton(onClick = {
                            topLevelRows.firstOrNull { it > listState.firstVisibleItemIndex }
                                ?.let { scope.launch { listState.animateScrollToItem(it) } }
                        }) { Icon(Icons.Default.KeyboardArrowDown, "Next comment") }
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
                        PostHeader(
                            post = post,
                            onVote = vm::votePost,
                            onToggleSave = vm::toggleSavePost,
                            onMore = { postSheet = true },
                        )
                    }
                    if (vm.focusCommentId != null) {
                        item(key = "focus") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Single comment thread",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { nav.replaceTop(PostRoute(post.id)) }) { Text("View all comments") }
                            }
                        }
                    }
                    items(visible, key = { (if (it.node is Comment) "c" else "m") + it.node.id }) { row ->
                        when (val node = row.node) {
                            is Comment -> CommentRow(
                                comment = node,
                                post = post,
                                collapsed = node.id in state.collapsed,
                                hiddenCount = row.hiddenCount,
                                onToggle = { vm.toggleCollapsed(node) },
                                onVote = { vm.voteComment(node, it) },
                                onReply = { reply(node.fullname, node.body) },
                                onDelete = { vm.delete(node.fullname) },
                            )
                            is MoreComments -> MoreRow(
                                more = node,
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

    if (postSheet) {
        state.post?.let { post ->
            PostActionsSheet(post = post, onDismiss = { postSheet = false }, onHide = null, onDelete = { vm.delete(post.fullname) })
        }
    }
}

@Composable
private fun PostHeader(post: Post, onVote: (Int) -> Unit, onToggleSave: () -> Unit, onMore: () -> Unit) {
    val nav = LocalNavigator.current
    Column {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaLine(post, showSubreddit = true, onSubreddit = { nav.push(FeedRoute(post.subreddit)) })
            Text(post.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        MediaPreview(post, onClick = { nav.openMedia(post) }, maxAspect = 3f, minAspect = 0.6f)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (post.media is PostMedia.Link) {
                Spacer(Modifier.height(4.dp))
                LinkRow(post, onClick = { nav.openMedia(post) })
            }
            post.selfTextHtml?.takeIf { it.isNotBlank() }?.let { html ->
                Spacer(Modifier.height(4.dp))
                Text(rememberRedditHtml(html), style = MaterialTheme.typography.bodyLarge)
            }
        }
        VoteBar(
            likes = post.likes,
            score = post.score,
            saved = post.saved,
            commentCount = post.numComments,
            onVote = onVote,
            onToggleSave = onToggleSave,
            onComments = null,
            onMore = onMore,
        )
        HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

private val INDENT = 12.dp
private const val MAX_INDENT_DEPTH = 8

@Composable
private fun CommentRow(
    comment: Comment,
    post: Post,
    collapsed: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
    onVote: (Int) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val isOwn = LocalAccount.current?.equals(comment.author, ignoreCase = true) == true
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val rail = ResyncColors.Depth[(comment.depth - 1).coerceAtLeast(0) % ResyncColors.Depth.size]
    val indent = INDENT * comment.depth.coerceAtMost(MAX_INDENT_DEPTH)

    Column {
        if (comment.depth == 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true })
                    .padding(start = 8.dp + indent, end = 14.dp)
                    .drawBehind {
                        if (comment.depth > 0) drawRect(rail, topLeft = Offset(0f, 0f), size = Size(2.dp.toPx(), size.height))
                    }
                    .padding(start = if (comment.depth > 0) 10.dp else 6.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CommentHeader(comment, collapsed, hiddenCount)
                if (!collapsed) Text(rememberRedditHtml(comment.bodyHtml), style = MaterialTheme.typography.bodyMedium)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val close = { menuOpen = false }
                MenuEntry("Reply", close, onReply)
                MenuEntry(if (comment.likes == true) "Remove upvote" else "Upvote", close) { onVote(if (comment.likes == true) 0 else 1) }
                MenuEntry(if (comment.likes == false) "Remove downvote" else "Downvote", close) { onVote(if (comment.likes == false) 0 else -1) }
                if (comment.author != "[deleted]") MenuEntry("u/${comment.author}", close) { nav.push(UserRoute(comment.author)) }
                MenuEntry("Copy text", close) { clipboard.setText(AnnotatedString(comment.body)) }
                MenuEntry("Share link", close) { nav.share("https://www.reddit.com${post.permalink}${comment.id}/", post.title) }
                if (isOwn) {
                    MenuEntry("Edit", close) {
                        nav.push(ComposeRoute(ComposeMode.EDIT, target = comment.fullname, quote = post.title, initial = comment.body))
                    }
                    MenuEntry("Delete", close) { confirmDelete = true }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog("comment", onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun MenuEntry(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { close(); action() })
}

@Composable
private fun CommentHeader(comment: Comment, collapsed: Boolean, hiddenCount: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val authorColor = when {
        comment.distinguished == "moderator" -> ResyncColors.Moderator
        comment.distinguished == "admin" -> ResyncColors.Nsfw
        comment.isSubmitter -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val scoreColor = when (comment.likes) {
        true -> ResyncColors.Upvote
        false -> ResyncColors.Downvote
        null -> muted
    }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = authorColor, fontWeight = FontWeight.SemiBold)) { append(comment.author) }
        if (comment.isSubmitter) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(" OP") }
        if (comment.stickied) withStyle(SpanStyle(color = ResyncColors.Moderator)) { append(" · pinned") }
        comment.flair?.let { withStyle(SpanStyle(color = muted)) { append(" · $it") } }
        append("  ")
        withStyle(SpanStyle(color = scoreColor, fontWeight = FontWeight.Medium)) {
            append(if (comment.scoreHidden) "–" else Format.count(comment.score))
        }
        withStyle(SpanStyle(color = muted)) {
            append(" · ${Format.timeAgo(comment.createdUtc)}")
            if (comment.edited) append(" ✱")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.weight(1f))
        if (collapsed) {
            Text(
                if (hiddenCount > 0) "+$hiddenCount" else "collapsed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MoreRow(more: MoreComments, loading: Boolean, onClick: () -> Unit) {
    val indent = INDENT * more.depth.coerceAtMost(MAX_INDENT_DEPTH)
    val rail = ResyncColors.Depth[(more.depth - 1).coerceAtLeast(0) % ResyncColors.Depth.size]
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(start = 8.dp + indent, end = 14.dp)
            .drawBehind {
                if (more.depth > 0) drawRect(rail, size = Size(2.dp.toPx(), size.height))
            }
            .padding(start = if (more.depth > 0) 10.dp else 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                more.children.isEmpty() -> "Continue this thread →"
                more.count == 1 -> "Load 1 more reply"
                else -> "Load ${more.count} more replies"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (loading) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CommentSortMenu(onPick: (CommentSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.Sort, "Sort comments") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CommentSort.entries.forEach { sort ->
                DropdownMenuItem(text = { Text(sort.label) }, onClick = { open = false; onPick(sort) })
            }
        }
    }
}
