package com.nhn.gps.location.phone.tracker.ads

import com.google.android.gms.ads.nativead.NativeAdOptions
import com.leansoft.ads.AdConfig
import com.nhn.gps.location.phone.tracker.BuildConfig
import com.nhn.gps.location.phone.tracker.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsAdConfig @Inject constructor() : AdConfig() {
    override fun getLayoutLoading(): Int = R.layout.dialog_loading_ad
    override fun nativeAdChoicesPosition(): Int = NativeAdOptions.ADCHOICES_TOP_LEFT
    override fun blockRootedDevice(): Boolean = !BuildConfig.DEBUG && super.blockRootedDevice()
}
