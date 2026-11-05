package io.github.radixtrator.resync.ui.common

import java.util.Locale
import kotlin.math.abs

object Format {
    /** Sync names feeds plainly: "Frontpage" (null or ""), "Popular", "All", or the subreddit as is. */
    fun feedName(subreddit: String?): String = when (subreddit?.lowercase()) {
        null, "" -> "Frontpage"
        "popular" -> "Popular"
        "all" -> "All"
        else -> subreddit
    }

    fun timeAgo(epochSeconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val s = (nowSeconds - epochSeconds).coerceAtLeast(0)
        return when {
            s < 60 -> "now"
            s < 3_600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3_600}h"
            s < 86_400 * 30 -> "${s / 86_400}d"
            s < 86_400 * 365 -> "${s / (86_400 * 30)}mo"
            else -> "${s / (86_400 * 365)}y"
        }
    }

    fun count(n: Int): String = when {
        abs(n) < 1_000 -> n.toString()
        abs(n) < 100_000 -> String.format(Locale.ROOT, "%.1fk", n / 1_000.0).replace(".0k", "k")
        abs(n) < 1_000_000 -> "${n / 1_000}k"
        else -> String.format(Locale.ROOT, "%.1fm", n / 1_000_000.0).replace(".0m", "m")
    }
}

/** Reddit's tri-state vote ("likes") as -1/0/1. */
fun voteValue(likes: Boolean?): Int = when (likes) {
    true -> 1
    false -> -1
    null -> 0
}

fun likesFor(direction: Int): Boolean? = when {
    direction > 0 -> true
    direction < 0 -> false
    else -> null
}
