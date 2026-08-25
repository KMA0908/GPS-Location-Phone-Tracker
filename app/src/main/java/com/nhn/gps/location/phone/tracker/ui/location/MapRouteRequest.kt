package com.nhn.gps.location.phone.tracker.ui.location

import java.util.UUID

data class MapRouteRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val friendId: String? = null,
    val source: String = "famous_place",
)
