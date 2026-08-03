package com.nhn.gps.location.phone.tracker.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val avatarUrl: String = ""
)
