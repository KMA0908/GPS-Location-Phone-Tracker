package com.nhn.gps.location.phone.tracker.ads

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object GpsRemoteConfig {
    fun showNativeFullOnboardingClose(): Boolean = runCatching {
        FirebaseRemoteConfig.getInstance().getBoolean("native_full_ob_close_btn")
    }.getOrDefault(true)

    fun languageNextButtonDelayMillis(): Long = runCatching {
        FirebaseRemoteConfig.getInstance().getLong("delay_btn_next_lfo")
            .coerceAtLeast(0L) * 1_000L
    }.getOrDefault(0L)
}
