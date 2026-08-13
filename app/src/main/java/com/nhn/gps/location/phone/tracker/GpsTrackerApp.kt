package com.nhn.gps.location.phone.tracker

import com.google.android.libraries.places.api.Places
import com.leansoft.ads.AdsApplication
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GpsTrackerApp : AdsApplication() {
    override fun onCreate() {
        super.onCreate()
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, getString(R.string.maps_api_key))
        }
    }
}
