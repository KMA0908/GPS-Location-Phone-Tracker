package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

internal object LocationMovementPolicy {
    const val DISPLAY_AND_SYNC_THRESHOLD_METERS = 100.0

    fun isValid(location: LatLng): Boolean =
        location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            !(location.latitude == 0.0 && location.longitude == 0.0)

    fun shouldAccept(
        previous: LatLng?,
        current: LatLng,
        thresholdMeters: Double = DISPLAY_AND_SYNC_THRESHOLD_METERS,
    ): Boolean {
        if (!isValid(current)) return false
        if (previous == null || !isValid(previous)) return true
        return SphericalUtil.computeDistanceBetween(previous, current) >= thresholdMeters
    }
}
