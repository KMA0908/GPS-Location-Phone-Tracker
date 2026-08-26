package com.nhn.gps.location.phone.tracker.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneNearPolicyTest {
    @Test
    fun dangerousZoneWithinOneHundredMetersOfBoundaryIsNear() {
        assertTrue(ZoneNearPolicy.isNearDangerousZone(true, false, 200.0, 100.0))
    }

    @Test
    fun safeInsideOrFarLocationIsNotNearDangerousZone() {
        assertFalse(ZoneNearPolicy.isNearDangerousZone(false, false, 150.0, 100.0))
        assertFalse(ZoneNearPolicy.isNearDangerousZone(true, true, 90.0, 100.0))
        assertFalse(ZoneNearPolicy.isNearDangerousZone(true, false, 201.0, 100.0))
    }

    @Test
    fun onlyKnownOutsideFarToNearTransitionAlerts() {
        assertTrue(ZoneNearPolicy.shouldAlert(false, false, false, true))
        assertFalse(ZoneNearPolicy.shouldAlert(null, false, null, true))
        assertFalse(ZoneNearPolicy.shouldAlert(true, true, false, true))
        assertFalse(ZoneNearPolicy.shouldAlert(false, false, true, true))
    }
}
