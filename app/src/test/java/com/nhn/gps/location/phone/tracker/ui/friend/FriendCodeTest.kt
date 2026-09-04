package com.nhn.gps.location.phone.tracker.ui.friend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendCodeTest {
    @Test
    fun generatedQrPayload_roundTrips() {
        val uid = "ABC-123"
        assertEquals(uid, FriendCode.parse(FriendCode.encode(uid)))
    }

    @Test
    fun manuallyEnteredId_isAccepted() {
        assertEquals("ABC-123", FriendCode.parse("  ABC-123  "))
        assertEquals("abc-123", FriendCode.parse("gps_friend:abc-123"))
    }

    @Test
    fun foreignQrAndEmptyPayload_areRejected() {
        assertNull(FriendCode.parse("https://example.com/profile"))
        assertNull(FriendCode.parse("gps_friend:   "))
        assertNull(FriendCode.parse(""))
    }
}
