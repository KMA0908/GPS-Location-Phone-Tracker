package com.nhn.gps.location.phone.tracker.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebasePathKeyTest {
    @Test
    fun installationAndPhoneKeys_areAccepted() {
        assertTrue(FirebasePathKey.isValid("A1B2C3D4-E5F6"))
        assertTrue(FirebasePathKey.isValid("0912345678"))
        assertTrue(FirebasePathKey.isValid("+84912345678"))
    }

    @Test
    fun blankPathAndControlCharacters_areRejected() {
        assertFalse(FirebasePathKey.isValid(""))
        assertFalse(FirebasePathKey.isValid("friend/id"))
        assertFalse(FirebasePathKey.isValid("friend.id"))
        assertFalse(FirebasePathKey.isValid("friend#id"))
        assertFalse(FirebasePathKey.isValid("friend\u0000id"))
    }

    @Test
    fun oversizedKey_isRejected() {
        assertFalse(FirebasePathKey.isValid("a".repeat(129)))
    }
}
