package io.github.radixtrator.resync.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.MainActivity
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.PostRoute

/** What the background checks (watched communities, the inbox) share: permission, channels, and tap targets. */
object Notifications {
    private const val EXTRA_POST = "io.github.radixtrator.resync.POST"
    private const val EXTRA_COMMENT = "io.github.radixtrator.resync.COMMENT"
    private const val EXTRA_INBOX = "io.github.radixtrator.resync.INBOX"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channel(context: Context, id: String, name: String, description: String) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply { this.description = description },
        )
    }

    /** Opens a post, or with [commentId] that comment in its thread. */
    fun openPost(context: Context, requestCode: Int, postId: String, commentId: String? = null): PendingIntent =
        open(context, requestCode) { putExtra(EXTRA_POST, postId).putExtra(EXTRA_COMMENT, commentId) }

    fun openInbox(context: Context, requestCode: Int): PendingIntent = open(context, requestCode) { putExtra(EXTRA_INBOX, true) }

    /** The page a tapped notification asks MainActivity for, if any. */
    fun routeFrom(intent: Intent): NavKey? {
        intent.getStringExtra(EXTRA_POST)?.let { post ->
            val comment = intent.getStringExtra(EXTRA_COMMENT)
            return PostRoute(post, comment, context = if (comment != null) 3 else 0)
        }
        return if (intent.getBooleanExtra(EXTRA_INBOX, false)) InboxRoute(home = true) else null
    }

    private fun open(context: Context, requestCode: Int, extras: Intent.() -> Intent): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).extras().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
