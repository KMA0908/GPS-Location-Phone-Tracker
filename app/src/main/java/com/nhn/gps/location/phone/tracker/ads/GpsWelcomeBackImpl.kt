package com.nhn.gps.location.phone.tracker.ads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
/*
import com.leansoft.ads.ui.welcome_back.LeansoftWelcomeBackInterface
import com.leansoft.ads.view.NativeAdViewContainer
*/
import com.nhn.gps.location.phone.tracker.R

// TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
class GpsWelcomeBackImpl /* : LeansoftWelcomeBackInterface() */ {
    private var view: View? = null
    private var listener: (() -> Unit)? = null
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_ads_gate, container, false).also { root ->
            view = root
            root.findViewById<View>(R.id.btnContinue).setOnClickListener { listener?.invoke() }
        }
    fun onViewCreated(view: View, savedInstanceState: Bundle?) = Unit
    // fun getNativeAdViewContainer(): NativeAdViewContainer =
    //    requireNotNull(view).findViewById(R.id.nativeAdViewContainer)
    fun updateCountdownSecond(remainTime: Int) {
        view?.findViewById<TextView>(R.id.btnContinue)?.text = if (remainTime > 0) "Continue ($remainTime)" else "Continue"
    }
    fun updateEnableButtonNext(enable: Boolean) { view?.findViewById<View>(R.id.btnContinue)?.isEnabled = enable }
    fun registerGoToMainListener(listener: () -> Unit) { this.listener = listener }
}
