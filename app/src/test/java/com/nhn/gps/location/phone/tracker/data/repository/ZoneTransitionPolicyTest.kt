package com.nhn.gps.location.phone.tracker.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTransitionPolicyTest {
    @Test
    fun unknownInsideInitializesWithoutAnExitTransition() {
        val decision = ZoneTransitionPolicy.evaluate(null, 50.0, 100.0, 10f)
        assertTrue(decision.accepted)
        assertTrue(decision.inside == true)
        assertTrue(decision.stateChanged)
    }

    @Test
    fun unknownOutsideInitializesOutside() {
        val decision = ZoneTransitionPolicy.evaluate(null, 150.0, 100.0, 10f)
        assertTrue(decision.inside == false)
        assertTrue(decision.stateChanged)
    }

    @Test
    fun insideToOutsideRequiresCrossingTheHysteresisMargin() {
        val nearBoundary = ZoneTransitionPolicy.evaluate(true, 105.0, 100.0, 10f)
        val outside = ZoneTransitionPolicy.evaluate(true, 111.0, 100.0, 10f)
        assertTrue(nearBoundary.inside == true)
        assertFalse(nearBoundary.stateChanged)
        assertTrue(outside.inside == false)
        assertTrue(outside.stateChanged)
    }

    @Test
    fun outsideToInsideRequiresCrossingTheHysteresisMargin() {
        val nearBoundary = ZoneTransitionPolicy.evaluate(false, 95.0, 100.0, 10f)
        val inside = ZoneTransitionPolicy.evaluate(false, 89.0, 100.0, 10f)
        assertTrue(nearBoundary.inside == false)
        assertFalse(nearBoundary.stateChanged)
        assertTrue(inside.inside == true)
        assertTrue(inside.stateChanged)
    }

    @Test
    fun poorAccuracyDoesNotChangeZoneState() {
        val decision = ZoneTransitionPolicy.evaluate(true, 150.0, 100.0, 150f)
        assertFalse(decision.accepted)
        assertTrue(decision.inside == true)
        assertFalse(decision.stateChanged)
    }

    @Test
    fun poorAccuracyDoesNotInitializeUnknownState() {
        val decision = ZoneTransitionPolicy.evaluate(null, 50.0, 100.0, Float.NaN)
        assertFalse(decision.accepted)
        assertNull(decision.inside)
    }
}
