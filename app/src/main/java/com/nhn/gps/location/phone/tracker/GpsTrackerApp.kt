package com.nhn.gps.location.phone.tracker

import com.google.android.libraries.places.api.Places
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
// import com.leansoft.ads.AdsApplication
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
class GpsTrackerApp : Application() /* AdsApplication() */ {
    override fun onCreate() {
        super.onCreate()
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, getString(R.string.maps_api_key))
        }
    }
}
