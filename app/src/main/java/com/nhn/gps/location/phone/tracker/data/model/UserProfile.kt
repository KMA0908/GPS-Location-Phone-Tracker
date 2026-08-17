package com.nhn.gps.location.phone.tracker.data.model

import android.os.Parcelable
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@IgnoreExtraProperties
@Parcelize
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val avatarKey: String = ""
) : Parcelable
