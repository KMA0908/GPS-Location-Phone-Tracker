package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMovementPolicyTest {
    private val origin = LatLng(21.028511, 105.804817)

    @Test
    fun firstValidLocationIsAccepted() {
        assertTrue(LocationMovementPolicy.shouldAccept(null, origin))
    }

    @Test
    fun movementBelowThresholdIsIgnored() {
        val aboutFiftyMetersNorth = LatLng(origin.latitude + 0.00045, origin.longitude)
        assertFalse(LocationMovementPolicy.shouldAccept(origin, aboutFiftyMetersNorth))
    }

    @Test
    fun movementBeyondThresholdIsAccepted() {
        val aboutOneHundredFiftyMetersNorth = LatLng(origin.latitude + 0.00135, origin.longitude)
        assertTrue(LocationMovementPolicy.shouldAccept(origin, aboutOneHundredFiftyMetersNorth))
    }

    @Test
    fun movementAtNinetyNineMetersIsIgnored() {
        val destination = SphericalUtil.computeOffset(origin, 99.0, 0.0)
        assertFalse(LocationMovementPolicy.shouldAccept(origin, destination))
    }

    @Test
    fun movementBeyondOneHundredMetersIsAccepted() {
        val destination = SphericalUtil.computeOffset(origin, 100.1, 0.0)
        assertTrue(LocationMovementPolicy.shouldAccept(origin, destination))
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertFalse(LocationMovementPolicy.shouldAccept(null, LatLng(0.0, 0.0)))
        assertFalse(LocationMovementPolicy.shouldAccept(null, LatLng(Double.NaN, 10.0)))
    }
}
