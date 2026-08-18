package com.nhn.gps.location.phone.tracker.ads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.leansoft.ads.ui.uninstall.LeansoftUninstallInterface
import com.leansoft.ads.view.NativeAdViewContainer
import com.nhn.gps.location.phone.tracker.R

class GpsUninstallImpl : LeansoftUninstallInterface {
    private var view: View? = null
    private var listener: ((Bundle) -> Unit)? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_ads_gate, container, false).also { root ->
            view = root
            root.findViewById<View>(R.id.btnContinue).setOnClickListener { listener?.invoke(Bundle()) }
        }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = Unit
    override fun getNativeAdViewContainer(): NativeAdViewContainer =
        requireNotNull(view).findViewById(R.id.nativeAdViewContainer)
    override fun registerGoToMainListener(listener: (Bundle) -> Unit) { this.listener = listener }
}
