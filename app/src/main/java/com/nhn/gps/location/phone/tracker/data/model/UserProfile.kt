package com.nhn.gps.location.phone.tracker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val avatarKey: String = "",
    val friendIds: List<String> = emptyList(),
    val hasOnline: Boolean = false,
    val trackingAvailable: Boolean = false,
    val secondaryPhones: Map<String, String> = emptyMap(),
) : Parcelable
