package com.nhn.gps.location.phone.tracker.ads

import android.util.Log
import androidx.fragment.app.FragmentManager
import com.leansoft.ads.AdManager

object GpsAds {
    private const val TAG = "GpsAds"

    fun showInterThen(
        placement: String,
        forceShow: Boolean = false,
        showLoading: Boolean = true,
        timeoutMillis: Long? = null,
        fragmentManager: FragmentManager? = null,
        showNativeFullAfterInter: Boolean = placement != GpsAdPlacement.INTER_SPLASH,
        next: () -> Unit,
    ) {
        var continued = false
        fun continueOnce() {
            if (continued) return
            continued = true
            runCatching(next).onFailure { Log.e(TAG, "Continuation failed: $placement", it) }
        }

        runCatching {
            if (!AdManager.instance.adEnablePlacement(placement)) {
                continueOnce()
                return@runCatching
            }
            AdManager.instance.showInterAd(
                placement,
                forceShow,
                showLoading,
                timeoutMillis,
            ) { showed ->
                val canShowNativeFull = showed &&
                    showNativeFullAfterInter &&
                    fragmentManager != null &&
                    !fragmentManager.isDestroyed &&
                    !fragmentManager.isStateSaved &&
                    AdManager.instance.adEnablePlacement(GpsAdPlacement.NATIVE_FULL)
                if (canShowNativeFull) {
                    runCatching {
                        AdManager.instance.showNativeFullDialog(
                            fragmentManager,
                            GpsAdPlacement.NATIVE_FULL,
                            false,
                        ) { continueOnce() }
                    }.onFailure {
                        Log.e(TAG, "Native full failed after $placement", it)
                        continueOnce()
                    }
                } else {
                    continueOnce()
                }
            }
        }.onFailure {
            Log.e(TAG, "Interstitial flow failed: $placement", it)
            continueOnce()
        }
    }

    fun showAppOpenThen(
        placement: String = GpsAdPlacement.AOA_RESUME,
        showLoading: Boolean = false,
        timeoutMillis: Long? = null,
        next: () -> Unit = {},
    ) {
        var continued = false
        fun continueOnce() {
            if (continued) return
            continued = true
            runCatching(next).onFailure { Log.e(TAG, "AOA continuation failed", it) }
        }

        runCatching {
            if (!AdManager.instance.adEnablePlacement(placement)) {
                continueOnce()
                return@runCatching
            }
            AdManager.instance.showAppOpenAd(
                placement,
                timeoutMillis,
                showLoading,
            ) {
                runCatching { AdManager.instance.preloadAppOpenAd(placement) }
                continueOnce()
            }
        }.onFailure {
            Log.e(TAG, "App-open flow failed: $placement", it)
            continueOnce()
        }
    }
}
