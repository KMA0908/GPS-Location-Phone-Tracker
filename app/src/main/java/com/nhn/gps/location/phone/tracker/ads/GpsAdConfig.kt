package com.nhn.gps.location.phone.tracker.ads

import com.google.android.gms.ads.nativead.NativeAdOptions
import com.leansoft.ads.AdConfig
import com.nhn.gps.location.phone.tracker.BuildConfig
import com.nhn.gps.location.phone.tracker.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsAdConfig @Inject constructor() : AdConfig() {
    override fun adUnitPlacement(placement: String): List<String> = when (placement) {
        in GpsAdPlacement.appOpen -> listOf(TEST_APP_OPEN_ID)
        in GpsAdPlacement.banners -> listOf(TEST_BANNER_ID)
        in GpsAdPlacement.interstitials -> listOf(TEST_INTERSTITIAL_ID)
        in GpsAdPlacement.natives -> listOf(TEST_NATIVE_ID)
        else -> emptyList()
    }

    override fun getLayoutLoading(): Int = R.layout.dialog_loading_ad
    override fun nativeAdChoicesPosition(): Int = NativeAdOptions.ADCHOICES_TOP_LEFT
    override fun blockRootedDevice(): Boolean = !BuildConfig.DEBUG && super.blockRootedDevice()

    companion object {
        // Keep the ad SDK wired for later re-enable, but match comment_ads by
        // disabling every placement in the current develop build.
        const val ADS_ENABLED = true
        const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
        const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
    }
}
