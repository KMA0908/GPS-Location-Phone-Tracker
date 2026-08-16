package com.nhn.gps.location.phone.tracker.ads

// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
/*
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.leansoft.ads.AdConfig
*/
import com.nhn.gps.location.phone.tracker.BuildConfig
import com.nhn.gps.location.phone.tracker.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
class GpsAdConfig @Inject constructor() /* : AdConfig() */ {
    fun getLayoutLoading(): Int = R.layout.dialog_loading_ad
    fun nativeAdChoicesPosition(): Int = 1
    fun blockRootedDevice(): Boolean = !BuildConfig.DEBUG /* && super.blockRootedDevice() */
}
