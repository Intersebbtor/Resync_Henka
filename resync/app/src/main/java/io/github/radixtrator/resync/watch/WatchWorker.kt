package io.github.radixtrator.resync.watch

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.ResyncApp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.SortKind
import io.github.radixtrator.resync.notify.Notifications
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sync's watch check, every hour: each watched community's newest posts are compared with the newest already
 * seen, and each newer post gets a notification; each watched post's comment count is compared with the last
 * one, and new comments get one notification per post.
 */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ResyncApp).container
        for (community in container.watch.watched.value.values) {
            val posts = try {
                container.api.posts(community.name, Sort(SortKind.NEW), after = null, limit = 10).items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check r/${community.name}", e)
                continue
            }
            val fresh = posts.filter { it.createdUtc > community.newestSeen && !it.stickied }.sortedBy { it.createdUtc }
            if (fresh.isEmpty()) continue
            // A burst of posts shouldn't bury the shade: the newest few are enough to send someone over.
            fresh.takeLast(MAX_PER_CHECK).forEach { notify(applicationContext, community.name, it) }
            container.watch.markSeen(community.name, fresh.last().createdUtc)
        }
        checkPosts(container)
        return Result.success()
    }

    private suspend fun checkPosts(container: AppContainer) {
        val watched = container.watch.posts.value
        if (watched.isEmpty()) return
        for (id in watched.keys) {
            val post = try {
                container.api.postNow(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check watched post $id", e)
                continue
            }
            val added = post.numComments - (watched[post.id]?.comments ?: continue)
            if (added <= 0) continue
            notifyComments(applicationContext, post, added)
            container.watch.markComments(post.id, post.numComments)
        }
    }

    companion object {
        private const val TAG = "ResyncWatch"
        private const val WORK = "watched-communities"
        private const val MAX_PER_CHECK = 5
        private const val CHANNEL = "watched"
        private const val CHANNEL_POSTS = "watched-posts"
        private const val EXTRA_COMMUNITY = "io.github.radixtrator.resync.COMMUNITY"
        private const val EXTRA_WATCHED_POST = "io.github.radixtrator.resync.WATCHED_POST"
        private const val EXTRA_NOTIFICATION = "io.github.radixtrator.resync.NOTIFICATION"

        /** Runs the check hourly while anything is watched, and stops it when nothing is. */
        fun schedule(context: Context, anyWatched: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!anyWatched) {
                work.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<WatchWorker>(60, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Runs the check once, now (Sync's "Check watched subreddits" in its settings). */
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WatchWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }

        /** "3 new comments" on a watched post; tapping opens it, "Stop watching" unwatches it. */
        private fun notifyComments(context: Context, post: Post, added: Int) {
            if (!Notifications.canNotify(context)) return
            Notifications.channel(context, CHANNEL_POSTS, "Watched posts", "New comments on posts you watch")
            val id = ("comments:" + post.id).hashCode()
            val stop = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, StopWatchingReceiver::class.java)
                    .putExtra(EXTRA_WATCHED_POST, post.id)
                    .putExtra(EXTRA_NOTIFICATION, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_POSTS)
                .setSmallIcon(R.drawable.ic_notification_watch)
                .setContentTitle(if (added == 1) "1 new comment" else "$added new comments")
                .setContentText(post.title)
                .setSubText("r/${post.subreddit}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(post.title))
                .setContentIntent(Notifications.openPost(context, id, post.id))
                .setAutoCancel(true)
                .addAction(0, "Stop watching", stop)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }

        /** "r/pics" over the post's title. Tapping opens the post; "Stop watching" unwatches the community. */
        private fun notify(context: Context, community: String, post: Post) {
            if (!Notifications.canNotify(context)) return
            Notifications.channel(context, CHANNEL, "Watched communities", "New posts in communities you watch")
            val id = post.id.hashCode()
            val open = Notifications.openPost(context, id, post.id)
            val stop = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, StopWatchingReceiver::class.java)
                    .putExtra(EXTRA_COMMUNITY, community)
                    .putExtra(EXTRA_NOTIFICATION, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_watch)
                .setContentTitle("r/$community")
                .setContentText(post.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(post.title))
                .setWhen(post.createdUtc * 1000)
                .setShowWhen(true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup("watch:${community.lowercase()}")
                .addAction(0, "Stop watching", stop)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }
    }

    /** The notification's "Stop watching" button (Sync's CancelWatchReceiver), for a community or a post. */
    class StopWatchingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val container = (context.applicationContext as ResyncApp).container
            val community = intent.getStringExtra(EXTRA_COMMUNITY)
            val post = intent.getStringExtra(EXTRA_WATCHED_POST)
            when {
                community != null -> container.setWatching(community, false)
                post != null -> container.unwatchPost(post)
                else -> return
            }
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
            Toast.makeText(context, if (community != null) "Stopped watching r/$community" else "Stopped watching the post", Toast.LENGTH_SHORT).show()
        }
    }
}
