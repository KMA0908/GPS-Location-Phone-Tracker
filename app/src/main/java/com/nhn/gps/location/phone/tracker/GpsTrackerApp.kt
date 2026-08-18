package com.nhn.gps.location.phone.tracker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.places.api.Places
import com.leansoft.ads.AdManager
import com.leansoft.ads.AdsApplication
import com.nhn.gps.location.phone.tracker.analytics.GpsAnalyticsTracker
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAds
import com.nhn.gps.location.phone.tracker.ads.ResumeAdGuard
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.ui.splash.SplashActivity
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference

@HiltAndroidApp
class GpsTrackerApp : AdsApplication() {
    private var currentActivityRef: WeakReference<Activity>? = null

    override fun onCreate() {
        super.onCreate()
        GpsAnalyticsTracker.initialize(this)
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, getString(R.string.maps_api_key))
        }
        registerActivityLifecycleCallbacks(activityCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(ResumeAdObserver())
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            currentActivityRef = WeakReference(activity)
        }

        override fun onActivityStarted(activity: Activity) {
            currentActivityRef = WeakReference(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            currentActivityRef = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivityRef?.get() === activity) currentActivityRef = null
        }
    }

    private inner class ResumeAdObserver : DefaultLifecycleObserver {
        private var firstForeground = true

        override fun onStart(owner: LifecycleOwner) {
            if (firstForeground) {
                firstForeground = false
                return
            }
            val activity = currentActivityRef?.get()
            if (activity is SplashActivity || activity !is MainActivity) return
            if (ResumeAdGuard.shouldSkipResumeAd()) return
            if (!AdManager.instance.checkCanShowAdResume()) return
            GpsAds.showAppOpenThen(
                placement = GpsAdPlacement.AOA_RESUME,
                showLoading = false,
            )
        }
    }
}
