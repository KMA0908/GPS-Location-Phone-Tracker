package com.nhn.gps.location.phone.tracker.data.model

import androidx.annotation.DrawableRes

data class LanguageModel(
    val name: String,
    val nativeName: String,
    val languageCode: String,
    @param:DrawableRes val flagIconRes: Int,
    var isSelected: Boolean = false,
)
