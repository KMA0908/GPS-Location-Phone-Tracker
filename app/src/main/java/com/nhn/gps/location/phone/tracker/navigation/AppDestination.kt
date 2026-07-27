package com.nhn.gps.location.phone.tracker.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Permission : AppDestination("permission")
    data object Map : AppDestination("map")
    data object Tracking : AppDestination("tracking")
    data object Settings : AppDestination("settings")
}
