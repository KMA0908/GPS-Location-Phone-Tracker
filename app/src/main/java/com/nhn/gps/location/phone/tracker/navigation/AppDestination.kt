package com.nhn.gps.location.phone.tracker.navigation

sealed class AppDestination(val route: String) {
    data object Permission : AppDestination("permission")
    data object SetUpProfile : AppDestination("setup_profile")
    data object Home : AppDestination("home")
    data object Map : AppDestination("map")
    data object AddFriend : AppDestination("add_friend")
    data object MyFriend : AppDestination("my_friend")
    data object ShowQrFriend : AppDestination("show_qr_friend")
    data object Tracking : AppDestination("tracking")
    data object Settings : AppDestination("settings")
    data object MyZones : AppDestination("my_zones")
    data object CreateZone : AppDestination("create_zone")
    data object ZoneAlerts : AppDestination("zone_alerts")
    data object Notifications : AppDestination("notifications")
}
