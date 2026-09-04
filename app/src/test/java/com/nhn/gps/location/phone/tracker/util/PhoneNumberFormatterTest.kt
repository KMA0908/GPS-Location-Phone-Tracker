package com.nhn.gps.location.phone.tracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneNumberFormatterTest {
    private val formatter = PhoneNumberFormatter()

    @Test
    fun `normalizes Vietnamese national number`() {
        assertEquals("+84912345678", formatter.normalize("+84", "912 345 678"))
    }

    @Test
    fun `keeps Vietnamese leading zero`() {
        assertEquals("+84912345678", formatter.normalize("+84", "0912-345-678"))
    }

    @Test
    fun `converts Vietnamese international number`() {
        assertEquals("+84912345678", formatter.normalize("+84", "+84912345678"))
    }

    @Test
    fun `normalizes another country`() {
        assertEquals("+14155552671", formatter.normalize("+1", "(415) 555-2671"))
    }

    @Test
    fun `converts double zero international prefix`() {
        assertEquals("+442079460018", formatter.normalize("+84", "0044 20 7946 0018"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a number that is too short`() {
        formatter.normalize("+84", "123")
    }

    @Test
    fun `blank phone is not valid for a searchable profile`() {
        assertFalse(formatter.isValid("+84", ""))
    }
}
