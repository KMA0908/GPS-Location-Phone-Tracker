package com.nhn.gps.location.phone.tracker.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class FriendLocation(
    @get:Exclude @set:Exclude var id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val avatarKey: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
