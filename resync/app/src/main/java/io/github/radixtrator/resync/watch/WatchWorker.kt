package io.github.radixtrator.resync.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.radixtrator.resync.MainActivity
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.ResyncApp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.Sort
import io.github.radixtrator.resync.data.SortKind
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sync's watched-community check: every hour, each watched community's newest posts are compared with the
 * newest already seen, and each newer post gets a notification.
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
        return Result.success()
    }

    companion object {
        private const val TAG = "ResyncWatch"
        private const val WORK = "watched-communities"
        private const val MAX_PER_CHECK = 5
        private const val CHANNEL = "watched"
        const val EXTRA_POST = "io.github.radixtrator.resync.POST"
        private const val EXTRA_COMMUNITY = "io.github.radixtrator.resync.COMMUNITY"
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

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** "r/pics" over the post's title. Tapping opens the post; "Stop watching" unwatches the community. */
        private fun notify(context: Context, community: String, post: Post) {
            if (!canNotify(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Watched communities", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New posts in communities you watch"
                },
            )
            val id = post.id.hashCode()
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_POST, post.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
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

    /** The notification's "Stop watching" button (Sync's CancelWatchReceiver). */
    class StopWatchingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val community = intent.getStringExtra(EXTRA_COMMUNITY) ?: return
            (context.applicationContext as ResyncApp).container.setWatching(community, false)
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
            Toast.makeText(context, "Stopped watching r/$community", Toast.LENGTH_SHORT).show()
        }
    }
}
