package com.nhn.gps.location.phone.tracker.ads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
/*
import com.leansoft.ads.ui.uninstall.LeansoftUninstallInterface
import com.leansoft.ads.view.NativeAdViewContainer
*/
import com.nhn.gps.location.phone.tracker.R

// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
class GpsUninstallImpl /* : LeansoftUninstallInterface */ {
    private var view: View? = null
    private var listener: ((Bundle) -> Unit)? = null
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_ads_gate, container, false).also { root ->
            view = root
            root.findViewById<View>(R.id.btnContinue).setOnClickListener { listener?.invoke(Bundle()) }
        }
    fun onViewCreated(view: View, savedInstanceState: Bundle?) = Unit
    // fun getNativeAdViewContainer(): NativeAdViewContainer =
    //    requireNotNull(view).findViewById(R.id.nativeAdViewContainer)
    fun registerGoToMainListener(listener: (Bundle) -> Unit) { this.listener = listener }
}
