package com.nhn.gps.location.phone.tracker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Country(
    val name: String,
    val dialCode: String,
    val iso: String,
    val emoji: String
) : Parcelable
