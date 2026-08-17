package com.nhn.gps.location.phone.tracker.ui.main

import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.navigation.AppDestination

data class MainUiState(
    val appOpenCount: Int = 0,
    val currentDestination: AppDestination? = null,
    val currentRoute: String? = null,
    val famousPlaces: List<FamousPlaceModel> = emptyList()
)
