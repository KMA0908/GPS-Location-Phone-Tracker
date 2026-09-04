package com.nhn.gps.location.phone.tracker.util

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {
    private val now = 1_700_000_000_000L

    @Test
    fun recentLocation_isOnline() {
        assertEquals(
            "Online",
            TimeAgo.formatPresence(
                timestamp = now - TimeUnit.MINUTES.toMillis(1),
                hasOnline = true,
                trackingAvailable = true,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun staleOrMissingLocation_isOffline() {
        assertEquals(
            "Offline · Never",
            TimeAgo.formatPresence(0L, hasOnline = false, trackingAvailable = true, nowMillis = now),
        )
        val value = TimeAgo.formatPresence(
            now - TimeUnit.MINUTES.toMillis(11),
            hasOnline = true,
            trackingAvailable = true,
            nowMillis = now,
        )
        assertEquals("Sleep · 11 mins ago", value)
    }

    @Test
    fun sharingDisabled_hasPriorityOverPresence() {
        assertEquals(
            "Location sharing off",
            TimeAgo.formatPresence(0L, hasOnline = true, trackingAvailable = false),
        )
    }
}
