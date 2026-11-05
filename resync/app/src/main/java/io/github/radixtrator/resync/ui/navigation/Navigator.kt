package io.github.radixtrator.resync.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.Settings

/**
 * Screen-to-screen navigation plus link handling. Provided as both LocalNavigator and LocalUriHandler,
 * so links tapped inside rendered markdown get the same in-app routing.
 */
class Navigator(
    private val context: Context,
    private val backStack: MutableList<NavKey>,
    private val settings: () -> Settings,
) : UriHandler {

    val canPop: Boolean get() = backStack.size > 1

    val top: NavKey get() = backStack.last()

    fun push(route: NavKey) {
        backStack.add(route)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Pops while [predicate] holds for the top screen, never past the root. */
    fun popWhile(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && predicate(backStack.last())) backStack.removeAt(backStack.lastIndex)
    }

    /** Swaps the current screen, e.g. the submit form for the post it created. */
    fun replaceTop(route: NavKey) {
        backStack.add(route)
        backStack.removeAt(backStack.lastIndex - 1)
    }

    fun resetTo(route: NavKey) {
        backStack.add(route)
        backStack.subList(0, backStack.lastIndex).clear()
    }

    override fun openUri(uri: String) {
        RedditLinks.route(uri)?.let(::push) ?: openExternal(uri)
    }

    fun openExternal(uri: String) {
        try {
            if (settings().inAppBrowser && uri.startsWith("http")) {
                CustomTabsIntent.Builder().setShowTitle(true).build().apply {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }.launchUrl(context, uri.toUri())
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open $uri", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** What tapping a post's media does: viewer for images/galleries, player for video, else the link. */
    fun openMedia(post: Post) {
        when (val m = post.media) {
            is PostMedia.Image -> push(ImageRoute(listOf(m.url)))
            is PostMedia.Gallery -> push(ImageRoute(m.images.map { it.url }))
            is PostMedia.Video -> push(VideoRoute(m.url, loop = m.loop))
            is PostMedia.Link -> openUri(m.url)
            PostMedia.None -> push(PostRoute(post.id))
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
val LocalSettings = compositionLocalOf { Settings() }

/** Active account name, or null when logged out. */
val LocalAccount = compositionLocalOf<String?> { null }
