package com.nhn.gps.location.phone.tracker.ads

import com.nhn.gps.location.phone.tracker.navigation.AppDestination

object GpsAdScenario {
    sealed interface ScreenAd {
        val placement: String

        data class Banner(override val placement: String) : ScreenAd
        data class Native(
            override val placement: String,
            val format: GpsAdViewBinder.NativeFormat,
        ) : ScreenAd
    }

    fun screenAd(route: String): ScreenAd? = when (route) {
        AppDestination.Permission.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_PERMISSION)
        AppDestination.SetUpProfile.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_SETUP_PROFILE,
            GpsAdViewBinder.NativeFormat.MEDIUM,
        )
        AppDestination.Home.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_HOME)
        AppDestination.Map.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_REALTIME_TRACKER)
        AppDestination.AddFriend.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_ADD_FRIEND_QR,
            GpsAdViewBinder.NativeFormat.SMALL,
        )
        AppDestination.ShowQrFriend.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_FRIEND_DETAIL_QR,
            GpsAdViewBinder.NativeFormat.BIG,
        )
        AppDestination.PhoneLocator.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_PHONE_NUMBER)
        AppDestination.CreateZone().route -> ScreenAd.Banner(GpsAdPlacement.BANNER_CREATE_ZONE)
        AppDestination.AlertDetail.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_ALERT_DETAIL)
        AppDestination.ZoneDetail.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_ZONE_DETAILS,
            GpsAdViewBinder.NativeFormat.BIG,
        )
        AppDestination.ZoneAlerts.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_ZONE_ALERT,
            GpsAdViewBinder.NativeFormat.SMALL,
        )
        AppDestination.Notifications.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_NOTIFICATION_CENTER)
        AppDestination.FamousPlace.route -> ScreenAd.Native(
            GpsAdPlacement.NATIVE_PLACE,
            GpsAdViewBinder.NativeFormat.SMALL,
        )
        AppDestination.Explore.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_EARTH)
        AppDestination.PlaceDetail.route -> ScreenAd.Banner(GpsAdPlacement.BANNER_PLACE_DETAIL)
        else -> null
    }

    fun interstitialLeaving(route: String): String? = when (route) {
        AppDestination.Permission.route -> GpsAdPlacement.INTER_PERMISSION
        AppDestination.SetUpProfile.route -> GpsAdPlacement.INTER_SETUP_PROFILE
        AppDestination.Home.route -> GpsAdPlacement.INTER_HOME
        AppDestination.Map.route -> GpsAdPlacement.INTER_REALTIME_TRACKER
        AppDestination.AddFriend.route -> GpsAdPlacement.INTER_ADD_FRIEND
        AppDestination.MyFriend.route -> GpsAdPlacement.INTER_LIST_FRIEND
        AppDestination.ShowQrFriend.route -> GpsAdPlacement.INTER_FRIEND_DETAIL
        AppDestination.PhoneLocator.route -> GpsAdPlacement.INTER_PHONE_NUMBER
        AppDestination.MyZones.route -> GpsAdPlacement.INTER_ZONE
        AppDestination.CreateZone().route -> GpsAdPlacement.INTER_CREATE_ZONE
        AppDestination.ZoneDetail.route -> GpsAdPlacement.INTER_ZONE_DETAILS
        AppDestination.FamousPlace.route -> GpsAdPlacement.INTER_PLACE
        AppDestination.Explore.route -> GpsAdPlacement.INTER_EARTH
        AppDestination.PlaceDetail.route -> GpsAdPlacement.INTER_PLACE_DETAIL
        else -> null
    }
}
