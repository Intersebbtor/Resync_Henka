package io.github.radixtrator.resync.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.ResyncApp
import io.github.radixtrator.resync.data.InboxBox
import io.github.radixtrator.resync.data.InboxItem
import io.github.radixtrator.resync.data.MessageCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sync's "Periodically check for new messages": fetches the active account's unread inbox and notifies
 * about each item not announced before, with Sync's Mark read and Reply actions.
 */
class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ResyncApp).container
        if (container.username.value == null || !Notifications.canNotify(applicationContext)) return Result.success()
        val unread = try {
            container.api.inbox(InboxBox.UNREAD, after = null).items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check the inbox", e)
            return Result.success()
        }
        val fresh = container.notifiedMessages.unseen(unread)
        if (fresh.isEmpty()) return Result.success()
        fresh.sortedBy { it.createdUtc }.takeLast(MAX_PER_CHECK).forEach { notify(applicationContext, it) }
        container.notifiedMessages.remember(fresh.map { it.fullname })
        return Result.success()
    }

    companion object {
        private const val TAG = "ResyncInbox"
        private const val WORK = "inbox-check"
        private const val CHANNEL = "messages"
        private const val MAX_PER_CHECK = 5
        private const val ACTION_MARK_READ = "io.github.radixtrator.resync.MARK_READ"
        private const val ACTION_REPLY = "io.github.radixtrator.resync.REPLY"
        private const val EXTRA_FULLNAME = "io.github.radixtrator.resync.FULLNAME"
        private const val EXTRA_NOTIFICATION = "io.github.radixtrator.resync.NOTIFICATION"
        private const val KEY_REPLY = "reply"

        /** Follows the setting: a repeating check, one check now ("On launch"), or none. */
        fun schedule(context: Context, check: MessageCheck) {
            val work = WorkManager.getInstance(context)
            val minutes = check.minutes
            if (minutes == null) {
                work.cancelUniqueWork(WORK)
                if (check == MessageCheck.ON_LAUNCH) checkNow(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<InboxWorker>(minutes, TimeUnit.MINUTES).setConstraints(online()).build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<InboxWorker>().setConstraints(online()).build())
        }

        private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** "u/author" over the message; tapping opens the reply in its thread, or the inbox for a message. */
        private fun notify(context: Context, item: InboxItem) {
            Notifications.channel(context, CHANNEL, "Messages", "New messages and replies in your inbox")
            val id = item.fullname.hashCode()
            val body = Html.fromHtml(item.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim().ifEmpty { item.body }
            val open = if (item.isComment && item.postId != null) {
                Notifications.openPost(context, id, item.postId, item.commentId)
            } else {
                Notifications.openInbox(context, id)
            }
            val reply = NotificationCompat.Action.Builder(0, "Reply", action(context, ACTION_REPLY, item, id, mutable = true))
                .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply to u/${item.author}").build())
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_message)
                .setContentTitle("u/${item.author}")
                .setContentText(body)
                .setSubText(if (item.isComment) item.postTitle ?: item.subject else item.subject)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setWhen(item.createdUtc * 1000)
                .setShowWhen(true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(CHANNEL)
                .addAction(0, "Mark read", action(context, ACTION_MARK_READ, item, id, mutable = false))
                .addAction(reply)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }

        /** Reply needs a mutable intent so the system can add the typed text. */
        private fun action(context: Context, action: String, item: InboxItem, id: Int, mutable: Boolean): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                id + action.hashCode(),
                Intent(context, InboxActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_FULLNAME, item.fullname)
                    .putExtra(EXTRA_NOTIFICATION, id),
                (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }

    /** The notification's Mark read and Reply buttons (Sync's MessageReplyReceiver and friends). */
    class InboxActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val fullname = intent.getStringExtra(EXTRA_FULLNAME) ?: return
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
            val container = (context.applicationContext as ResyncApp).container
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                val result = runCatching {
                    when (intent.action) {
                        ACTION_MARK_READ -> container.api.markRead(listOf(fullname))
                        ACTION_REPLY -> if (!text.isNullOrEmpty()) {
                            container.api.reply(fullname, text)
                            container.api.markRead(listOf(fullname))
                        }
                    }
                    container.refreshInboxCount()
                }
                withContext(Dispatchers.Main) {
                    // Dismissing also ends the reply field's spinner.
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    val done = if (intent.action == ACTION_REPLY) "Reply sent" else "Marked read"
                    Toast.makeText(context, result.fold({ done }, { "Couldn't reach Reddit: ${it.message}" }), Toast.LENGTH_SHORT).show()
                }
                pending.finish()
            }
        }
    }
}
