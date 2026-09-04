package com.nhn.gps.location.phone.tracker.data.model

data class FriendLocation(
    var id: String = "",
    val name: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val avatarKey: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L,
    val hasOnline: Boolean = false,
    val trackingAvailable: Boolean = false,
)

const val FRIEND_LOCATION_MAX_AGE_MS = 60_000L

fun FriendLocation.hasVisibleSharedLocation(
    nowMillis: Long = System.currentTimeMillis(),
): Boolean = hasOnline && trackingAvailable &&
    latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
    !(latitude == 0.0 && longitude == 0.0) &&
    updatedAt > 0L && nowMillis >= updatedAt &&
    nowMillis - updatedAt <= FRIEND_LOCATION_MAX_AGE_MS
