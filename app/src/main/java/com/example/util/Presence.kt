package com.example.util

/**
 * Shared presence helpers: a user is considered ONLINE when their last_seen_at
 * heartbeat is within [ONLINE_WINDOW_MS] of now.
 */
object Presence {
    const val ONLINE_WINDOW_MS = 70_000L

    fun isOnline(lastSeenMs: Long?, now: Long = System.currentTimeMillis()): Boolean =
        lastSeenMs != null && now - lastSeenMs in 0..ONLINE_WINDOW_MS

    /** "Active now" / "Active 5m ago" / "Active 3h ago" / "Active 2d ago" / "Offline". */
    fun label(lastSeenMs: Long?, now: Long = System.currentTimeMillis()): String {
        if (lastSeenMs == null) return "Offline"
        val delta = now - lastSeenMs
        return when {
            delta < 0 || delta <= ONLINE_WINDOW_MS -> "Active now"
            delta < 60_000L -> "Active now"
            delta < 3_600_000L -> "Active ${delta / 60_000L}m ago"
            delta < 86_400_000L -> "Active ${delta / 3_600_000L}h ago"
            delta < 7 * 86_400_000L -> "Active ${delta / 86_400_000L}d ago"
            else -> "Offline"
        }
    }
}
