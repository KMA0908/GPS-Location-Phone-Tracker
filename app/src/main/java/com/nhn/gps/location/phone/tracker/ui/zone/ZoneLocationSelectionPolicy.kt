package com.nhn.gps.location.phone.tracker.ui.zone

import com.google.android.gms.maps.model.LatLng

internal data class PinnedZoneLocation(
    val location: LatLng,
    val address: String?,
    val placeId: String?,
)

internal object ZoneLocationSelectionPolicy {
    fun select(location: LatLng, address: String?, placeId: String?): PinnedZoneLocation? =
        if (isValid(location)) PinnedZoneLocation(location, address, placeId) else null

    fun afterCameraMoved(
        selection: PinnedZoneLocation?,
        @Suppress("UNUSED_PARAMETER") cameraTarget: LatLng,
    ): PinnedZoneLocation? = selection

    fun replace(
        previous: PinnedZoneLocation?,
        location: LatLng,
        address: String?,
        placeId: String?,
    ): PinnedZoneLocation? = select(location, address, placeId) ?: previous

    fun resolveForSave(selection: PinnedZoneLocation?): LatLng? =
        selection?.location?.takeIf(::isValid)

    private fun isValid(location: LatLng): Boolean =
        location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            !(location.latitude == 0.0 && location.longitude == 0.0)
}
