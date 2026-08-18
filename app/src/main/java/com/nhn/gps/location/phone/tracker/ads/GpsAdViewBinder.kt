package com.nhn.gps.location.phone.tracker.ads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.leansoft.ads.AdManager
import com.leansoft.ads.view.BannerAdViewContainer
import com.leansoft.ads.view.NativeAdViewContainer
import com.nhn.gps.location.phone.tracker.R

object GpsAdViewBinder {
    enum class NativeFormat { SMALL, MEDIUM, BIG }

    fun bindNative(host: ViewGroup, placement: String, format: NativeFormat) {
        val target = BoundAd(placement, AdKind.NATIVE, format)
        if (host.tag == target && host.childCount > 0) return
        clear(host)
        if (!isEnabled(placement)) return

        val layout = when (format) {
            NativeFormat.SMALL -> R.layout.view_ad_native_small
            NativeFormat.MEDIUM -> R.layout.view_ad_native_medium
            NativeFormat.BIG -> R.layout.view_ad_native_big
        }
        val container = LayoutInflater.from(host.context)
            .inflate(layout, host, false) as NativeAdViewContainer
        host.addView(container)
        host.tag = target
        host.visibility = View.VISIBLE
        container.adsPlacement = placement
    }

    fun bindBanner(host: ViewGroup, placement: String) {
        val target = BoundAd(placement, AdKind.BANNER, null)
        if (host.tag == target && host.childCount > 0) return
        clear(host)
        if (!isEnabled(placement)) return

        val container = LayoutInflater.from(host.context)
            .inflate(R.layout.view_ad_banner, host, false) as BannerAdViewContainer
        host.addView(container)
        host.tag = target
        host.visibility = View.VISIBLE
        container.placement = placement
    }

    fun clear(vararg hosts: ViewGroup?) {
        hosts.filterNotNull().forEach(::clearHost)
    }

    private fun clearHost(host: ViewGroup) {
        (host.tag as? BoundAd)?.let { current ->
            runCatching {
                when (current.kind) {
                    AdKind.NATIVE -> AdManager.instance.destroyNativeAd(current.placement)
                    AdKind.BANNER -> AdManager.instance.destroyBannerAd(current.placement)
                }
            }
        }
        host.removeAllViews()
        host.tag = null
        host.visibility = View.GONE
    }

    private fun isEnabled(placement: String): Boolean =
        runCatching { AdManager.instance.adEnablePlacement(placement) }.getOrDefault(false)

    private enum class AdKind { NATIVE, BANNER }

    private data class BoundAd(
        val placement: String,
        val kind: AdKind,
        val format: NativeFormat?,
    )
}
