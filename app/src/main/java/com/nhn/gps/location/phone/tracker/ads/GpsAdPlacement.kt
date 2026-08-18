package com.nhn.gps.location.phone.tracker.ads

/** Placements are kept in one place so code and Remote Config cannot drift apart. */
object GpsAdPlacement {
    const val AOA_RESUME = "aoa_resume"
    const val NATIVE_FULL = "native_full"
    const val INTER_BACK = "inter_back"

    const val BANNER_SPLASH = "banner_splash"
    const val INTER_SPLASH = "inter_splash"

    const val NATIVE_LANGUAGE = "native_language"
    const val NATIVE_CLICK = "native_click"
    const val NATIVE_OBD_1 = "native_obd_1"
    const val NATIVE_OBD_FULL_1 = "native_obd_full_1"
    const val NATIVE_OBD_2 = "native_obd_2"
    const val NATIVE_OBD_FULL_2 = "native_obd_full_2"
    const val NATIVE_OBD_3 = "native_obd_3"

    const val BANNER_PERMISSION = "banner_permission"
    const val INTER_PERMISSION = "inter_permission"
    const val NATIVE_SETUP_PROFILE = "native_setup_profile"
    const val INTER_SETUP_PROFILE = "inter_setup_profile"

    const val NATIVE_HOME = "native_home"
    const val BANNER_HOME = "banner_home"
    const val INTER_HOME = "inter_home"
    const val BANNER_REALTIME_TRACKER = "banner_realtime_tracker"
    const val INTER_REALTIME_TRACKER = "inter_realtime_tracker"
    const val BANNER_DIRECTION = "banner_direction"

    const val INTER_ADD_FRIEND = "inter_add_friend"
    const val NATIVE_ADD_FRIEND_QR = "native_add_friend_qr"
    const val NATIVE_ADD_FRIEND_CODE = "native_add_friend_code"
    const val NATIVE_QR_CAMERA = "native_qr_camera"
    const val NATIVE_LIST_FRIEND = "native_list_friend"
    const val INTER_LIST_FRIEND = "inter_list_friend"
    const val NATIVE_FRIEND_DETAIL = "native_friend_detail"
    const val INTER_FRIEND_DETAIL = "inter_friend_detail"
    const val NATIVE_FRIEND_DETAIL_QR = "native_friend_detail_qr"

    const val NATIVE_ZONE = "native_zone"
    const val INTER_ZONE = "inter_zone"
    const val BANNER_ZONE_DETAIL = "banner_zone_detail"
    const val INTER_ZONE_DETAIL = "inter_zone_detail"
    const val BANNER_CREATE_ZONE = "banner_create_zone"
    const val INTER_CREATE_ZONE = "inter_create_zone"
    const val NATIVE_ZONE_DETAILS = "native_zone_details"
    const val INTER_ZONE_DETAILS = "inter_zone_details"
    const val NATIVE_ZONE_ALERT = "native_zone_alert"
    const val BANNER_ALERT_DETAIL = "banner_alert_detail"
    const val BANNER_NOTIFICATION_CENTER = "banner_notification_center"

    const val BANNER_PHONE_NUMBER = "banner_phone_number"
    const val INTER_PHONE_NUMBER = "inter_phone_number"
    const val BANNER_PHONE_NUMBER_DETAIL = "banner_phone_number_detail"

    const val NATIVE_PLACE = "native_place"
    const val INTER_PLACE = "inter_place"
    const val BANNER_EARTH = "banner_earth"
    const val INTER_EARTH = "inter_earth"
    const val BANNER_PLACE_DETAIL = "banner_place_detail"
    const val INTER_PLACE_DETAIL = "inter_place_detail"

    val appOpen = setOf(AOA_RESUME)

    val banners = setOf(
        BANNER_SPLASH,
        BANNER_PERMISSION,
        BANNER_HOME,
        BANNER_REALTIME_TRACKER,
        BANNER_DIRECTION,
        BANNER_ZONE_DETAIL,
        BANNER_CREATE_ZONE,
        BANNER_ALERT_DETAIL,
        BANNER_NOTIFICATION_CENTER,
        BANNER_PHONE_NUMBER,
        BANNER_PHONE_NUMBER_DETAIL,
        BANNER_EARTH,
        BANNER_PLACE_DETAIL,
    )

    val interstitials = setOf(
        INTER_BACK,
        INTER_SPLASH,
        INTER_PERMISSION,
        INTER_SETUP_PROFILE,
        INTER_HOME,
        INTER_REALTIME_TRACKER,
        INTER_ADD_FRIEND,
        INTER_LIST_FRIEND,
        INTER_FRIEND_DETAIL,
        INTER_ZONE,
        INTER_ZONE_DETAIL,
        INTER_CREATE_ZONE,
        INTER_ZONE_DETAILS,
        INTER_PHONE_NUMBER,
        INTER_PLACE,
        INTER_EARTH,
        INTER_PLACE_DETAIL,
    )

    val natives = setOf(
        NATIVE_FULL,
        NATIVE_LANGUAGE,
        NATIVE_CLICK,
        NATIVE_OBD_1,
        NATIVE_OBD_FULL_1,
        NATIVE_OBD_2,
        NATIVE_OBD_FULL_2,
        NATIVE_OBD_3,
        NATIVE_SETUP_PROFILE,
        NATIVE_HOME,
        NATIVE_ADD_FRIEND_QR,
        NATIVE_ADD_FRIEND_CODE,
        NATIVE_QR_CAMERA,
        NATIVE_LIST_FRIEND,
        NATIVE_FRIEND_DETAIL,
        NATIVE_FRIEND_DETAIL_QR,
        NATIVE_ZONE,
        NATIVE_ZONE_DETAILS,
        NATIVE_ZONE_ALERT,
        NATIVE_PLACE,
    )

    val all = appOpen + banners + interstitials + natives
}
