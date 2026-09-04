package com.nhn.gps.location.phone.tracker.util

import java.util.concurrent.TimeUnit

object TimeAgo {
    fun format(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0) return "Never"

        val diff = (nowMillis - timestamp).coerceAtLeast(0L)
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$mins ${if (mins == 1L) "min" else "mins"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours ${if (hours == 1L) "hour" else "hours"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days ${if (days == 1L) "day" else "days"} ago"
            }
        }
    }

    fun formatPresence(
        timestamp: Long,
        hasOnline: Boolean,
        trackingAvailable: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        if (!trackingAvailable) return "Location sharing off"
        if (!hasOnline) return "Offline · ${format(timestamp, nowMillis)}"
        if (timestamp <= 0L) return "Online · Waiting for location"
        val age = (nowMillis - timestamp).coerceAtLeast(0L)
        return if (age <= ACTIVE_LOCATION_THRESHOLD_MILLIS) {
            "Online"
        } else {
            "Sleep · ${format(timestamp, nowMillis)}"
        }
    }

    private val ACTIVE_LOCATION_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(10)
}
