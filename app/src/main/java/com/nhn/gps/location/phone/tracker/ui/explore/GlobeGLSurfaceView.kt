package com.nhn.gps.location.phone.tracker.ui.explore

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class GlobeGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: GlobeRenderer? = null
    var isSupported = false
        private set

    init {
        checkGLESVersion()
    }

    private fun checkGLESVersion() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configInfo = activityManager.deviceConfigurationInfo
        
        if (configInfo.reqGlEsVersion >= 0x30000) {
            setEGLContextClientVersion(3)
            renderer = GlobeRenderer(context)
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY
            isSupported = true
        } else {
            isSupported = false
        }
    }

    override fun onResume() {
        if (isSupported) {
            super.onResume()
        }
    }

    override fun onPause() {
        if (isSupported) {
            super.onPause()
        }
    }
}
