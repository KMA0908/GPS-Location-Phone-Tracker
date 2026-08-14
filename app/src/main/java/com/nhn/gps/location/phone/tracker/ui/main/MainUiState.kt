package com.nhn.gps.location.phone.tracker.ui.main

import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel

data class MainUiState(
    val appOpenCount: Int = 0,
    val currentRoute: String? = null,
    val famousPlaces: List<FamousPlaceModel> = emptyList()
)
