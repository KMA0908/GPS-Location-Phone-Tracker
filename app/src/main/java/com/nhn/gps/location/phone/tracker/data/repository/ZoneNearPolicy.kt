package com.nhn.gps.location.phone.tracker.data.repository

internal object ZoneNearPolicy {
    const val DISTANCE_FROM_BOUNDARY_METERS = 100.0

    fun isNearDangerousZone(
        isDangerous: Boolean,
        isInside: Boolean,
        distanceMeters: Double,
        radiusMeters: Double,
    ): Boolean = isDangerous &&
        !isInside &&
        distanceMeters.isFinite() &&
        radiusMeters.isFinite() &&
        radiusMeters > 0.0 &&
        distanceMeters <= radiusMeters + DISTANCE_FROM_BOUNDARY_METERS

    fun shouldAlert(
        previousInside: Boolean?,
        stateChanged: Boolean,
        previousNear: Boolean?,
        isNearNow: Boolean,
    ): Boolean = previousInside == false &&
        !stateChanged &&
        previousNear == false &&
        isNearNow
}
