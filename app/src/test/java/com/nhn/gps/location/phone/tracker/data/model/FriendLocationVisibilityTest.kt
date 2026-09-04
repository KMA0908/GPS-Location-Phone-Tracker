package com.nhn.gps.location.phone.tracker.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendLocationVisibilityTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `fresh shared online location is visible`() {
        assertTrue(
            location(updatedAt = now - 60_000L).hasVisibleSharedLocation(now),
        )
    }

    @Test
    fun `location is hidden when sharing is off or provider is offline`() {
        assertFalse(location(tracking = false).hasVisibleSharedLocation(now))
        assertFalse(location(online = false).hasVisibleSharedLocation(now))
    }

    @Test
    fun `stale future and invalid coordinates are hidden`() {
        assertFalse(
            location(updatedAt = now - FRIEND_LOCATION_MAX_AGE_MS - 1L)
                .hasVisibleSharedLocation(now),
        )
        assertFalse(location(updatedAt = now + 1L).hasVisibleSharedLocation(now))
        assertFalse(location(latitude = 0.0, longitude = 0.0).hasVisibleSharedLocation(now))
        assertFalse(location(latitude = 91.0).hasVisibleSharedLocation(now))
    }

    private fun location(
        latitude: Double = 10.7769,
        longitude: Double = 106.7009,
        updatedAt: Long = now - 1_000L,
        online: Boolean = true,
        tracking: Boolean = true,
    ) = FriendLocation(
        latitude = latitude,
        longitude = longitude,
        updatedAt = updatedAt,
        hasOnline = online,
        trackingAvailable = tracking,
    )
}
