package com.nhn.gps.location.phone.tracker.navigation

sealed class AppDestination(val route: String) {
    data object Permission : AppDestination("permission")
    data object SetUpProfile : AppDestination("setup_profile")
    data object Home : AppDestination("home")
    data object Map : AppDestination("map")
    data object Tracking : AppDestination("tracking")
    data object Settings : AppDestination("settings")
}
