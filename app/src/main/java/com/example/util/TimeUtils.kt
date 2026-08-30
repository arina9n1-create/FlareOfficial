package com.example.util

import java.util.concurrent.TimeUnit

object TimeUtils {
    fun getRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "Just now"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        if (diff < 0) return "Just now"
        
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        
        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}
