package com.nhn.gps.location.phone.tracker.ui.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoWheelerCountryCoverageTest {
    @Test
    fun `Vietnam supports two wheeler routes`() {
        assertTrue(TwoWheelerCountryCoverage.isSupportedCountry("VN"))
    }

    @Test
    fun `country codes are normalized`() {
        assertTrue(TwoWheelerCountryCoverage.isSupportedCountry(" vn "))
    }

    @Test
    fun `Italy and United States do not support two wheeler routes`() {
        assertFalse(TwoWheelerCountryCoverage.isSupportedCountry("IT"))
        assertFalse(TwoWheelerCountryCoverage.isSupportedCountry("US"))
    }
}
