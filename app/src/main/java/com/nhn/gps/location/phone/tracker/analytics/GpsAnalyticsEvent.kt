package com.nhn.gps.location.phone.tracker.analytics

/** Event names from `STech - GPS Tracker- MasterSheet/4. Even Tracking.html`. */
object GpsAnalyticsEvent {
    const val SPLASH_VIEW = "splash_view"
    const val INTER_SPLASH_VIEW = "inter_splash_view"
    const val NATIVE_FULL_SPLASH_VIEW = "native_full_splash_view"
    const val LFO_VIEW = "lfo_view"
    const val LFO_DUP_VIEW = "lfo_dup_view"
    const val ONBOARDING_1_VIEW = "onboarding_1_view"
    const val ONBOARDING_FULL_1_VIEW = "onboarding_full_1_view"
    const val ONBOARDING_2_VIEW = "onboarding_2_view"
    const val ONBOARDING_FULL_2_VIEW = "onboarding_full_2_view"
    const val ONBOARDING_3_VIEW = "onboarding_3_view"
    const val HOME_VIEW = "home_view"

    /**
     * Leansoft 1.1.8 emits these from its splash, language, onboarding and impression callbacks.
     * Logging them again in app code would double Firebase counts.
     */
    val sdkManaged = setOf(
        SPLASH_VIEW,
        INTER_SPLASH_VIEW,
        NATIVE_FULL_SPLASH_VIEW,
        LFO_VIEW,
        LFO_DUP_VIEW,
        ONBOARDING_1_VIEW,
        ONBOARDING_FULL_1_VIEW,
        ONBOARDING_2_VIEW,
        ONBOARDING_FULL_2_VIEW,
        ONBOARDING_3_VIEW,
    )

    val all = sdkManaged + HOME_VIEW
}
