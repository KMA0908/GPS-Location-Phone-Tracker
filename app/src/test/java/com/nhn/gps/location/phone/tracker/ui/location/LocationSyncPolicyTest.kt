package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSyncPolicyTest {
    private val origin = LatLng(21.028511, 105.804817)

    @Test
    fun firstValidLocationSyncsWhenNetworkIsValidated() {
        assertTrue(LocationSyncPolicy.shouldSync(true, null, origin))
    }

    @Test
    fun locationDoesNotSyncWithoutValidatedNetwork() {
        assertFalse(LocationSyncPolicy.shouldSync(false, null, origin))
    }

    @Test
    fun movementBelowOneHundredMetersDoesNotSync() {
        val destination = SphericalUtil.computeOffset(origin, 99.0, 90.0)
        assertFalse(LocationSyncPolicy.shouldSync(true, origin, destination))
    }

    @Test
    fun latestLocationSyncsAfterNetworkReturnsAndMovementReachedThreshold() {
        val destination = SphericalUtil.computeOffset(origin, 100.1, 90.0)
        assertTrue(LocationSyncPolicy.shouldSync(true, origin, destination))
    }
}
