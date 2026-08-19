package com.nhn.gps.location.phone.tracker.navigation

sealed class AppDestination(val route: String) {
    data object Permission : AppDestination("permission")
    data object SetUpProfile : AppDestination("setup_profile")
    data object Home : AppDestination("home")
    data object Map : AppDestination("map")
    data object AddFriend : AppDestination("add_friend")
    data object MyFriend : AppDestination("my_friend")
    data class ShowQrFriend(val mode: com.nhn.gps.location.phone.tracker.ui.friend.FriendCodeDisplayMode) : AppDestination("show_qr_friend")
    data object Tracking : AppDestination("tracking")
    data object Settings : AppDestination("settings")
    data object PhoneLocator : AppDestination("phone_locator")
    data object MyZones : AppDestination("my_zones")
    data class CreateZone(
        val initialLatitude: Double? = null,
        val initialLongitude: Double? = null,
        val initialAddress: String? = null,
        val initialPlaceName: String? = null
    ) : AppDestination("create_zone")
    data object AlertDetail : AppDestination("alert_detail")
    data object ZoneDetail : AppDestination("zone_detail")
    data object ZoneAlerts : AppDestination("zone_alerts")
    data object Notifications : AppDestination("notifications")
    data object FamousPlace : AppDestination("famous_place")
    data object Explore : AppDestination("explore")
    data object PlaceDetail : AppDestination("place_detail")
    data object SettingsLanguage : AppDestination("settings_language")
}
