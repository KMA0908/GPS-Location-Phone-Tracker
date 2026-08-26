package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.model.LatLng

internal object LocationSyncPolicy {
    fun shouldSync(
        networkValidated: Boolean,
        previousSyncedLocation: LatLng?,
        currentLocation: LatLng,
    ): Boolean = networkValidated &&
        LocationMovementPolicy.shouldAccept(previousSyncedLocation, currentLocation)
}
