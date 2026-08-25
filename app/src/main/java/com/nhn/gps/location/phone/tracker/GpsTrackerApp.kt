package com.nhn.gps.location.phone.tracker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.leansoft.ads.AdManager
import com.leansoft.ads.AdsApplication
import com.nhn.gps.location.phone.tracker.analytics.GpsAnalyticsTracker
import com.nhn.gps.location.phone.tracker.ads.GpsAdConfig
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
        FirebaseApp.initializeApp(this)
        initializeAnonymousFirebaseSession()
        GpsAnalyticsTracker.initialize(this)
        enableLeanSoftDebugAds()
        registerActivityLifecycleCallbacks(activityCallbacks)
        if (GpsAdConfig.ADS_ENABLED) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(ResumeAdObserver())
        }
    }

    /**
     * Friend/location data is protected by authenticated Realtime Database rules.
     * Anonymous auth keeps that protection without adding a visible login flow.
     */
    private fun initializeAnonymousFirebaseSession() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return

        auth.signInAnonymously()
            .addOnFailureListener { error ->
                android.util.Log.e("FirebaseAuth", "Anonymous session initialization failed", error)
            }
    }

    /**
     * LeanSoft protects debug ad requests with an internal package allow-list.
     * Register this app in debug builds just like Call Theme does, then ask
     * Google Mobile Ads to treat the emulator/development phone as test devices.
     */
    private fun enableLeanSoftDebugAds() {
        if (!BuildConfig.DEBUG || !GpsAdConfig.ADS_ENABLED) return
        runCatching {
            val packageToken = Base64.encodeToString(
                packageName.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val manager = AdManager.instance
            manager.javaClass.declaredFields
                .filter { it.type == Array<String>::class.java }
                .forEach { field ->
                    field.isAccessible = true
                    val current = (field.get(manager) as? Array<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty()
                    if (!current.contains(packageToken)) {
                        field.set(manager, (current + packageToken).toTypedArray())
                    }
                }
            manager.setTestDeviceIDs(
                listOf(
                    AdRequest.DEVICE_ID_EMULATOR,
                    DEBUG_TEST_DEVICE_ID,
                ),
            )
        }
    }

    private val activityCallbacks = object : ActivityLifecycleCallbacks {
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

    private companion object {
        // Test device used by the shared Call Theme/GPS development setup.
        const val DEBUG_TEST_DEVICE_ID = "2BFE73829A1E25BF972AED0964145460"
    }
}
