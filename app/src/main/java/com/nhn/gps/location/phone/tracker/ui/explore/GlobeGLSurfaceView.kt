package com.nhn.gps.location.phone.tracker.ui.explore

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.sqrt

class GlobeGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: GlobeRenderer? = null
    var isSupported = false
        private set

    private var lastX = 0f
    private var lastY = 0f
    
    private var startX = 0f
    private var startY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    private val minDistance = 3.5f
    private val maxDistance = 15.0f

    var onCameraChanged: ((GlobeCameraState) -> Unit)? = null
    var onMarkerClicked: ((String) -> Unit)? = null

    private var animator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer?.let { r ->
                r.cameraDistance /= detector.scaleFactor
                r.cameraDistance = r.cameraDistance.coerceIn(minDistance, maxDistance)
                requestRender()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            notifyCameraChanged()
        }
    })

    init {
        checkGLESVersion()
    }

    private fun notifyCameraChanged() {
        getCameraState()?.let { state ->
            onCameraChanged?.invoke(state)
        }
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSupported) return false
        
        scaleDetector.onTouchEvent(event)
        
        if (scaleDetector.isInProgress) return true

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY

                if (dx != 0f || dy != 0f) {
                    renderer?.let { r ->
                        // Rotate around Y axis (horizontal drag rotates globe left/right)
                        r.angleY += dx * 0.5f
                        // Rotate around X axis (vertical drag rotates globe up/down)
                        r.angleX += dy * 0.5f

                        // Limit pitch to avoid flipping
                        r.angleX = r.angleX.coerceIn(-90f, 90f)

                        requestRender()
                    }
                }

                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = x - startX
                val dy = y - startY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (event.action == MotionEvent.ACTION_UP && distance < touchSlop) {
                    // It's a tap
                    renderer?.let { r ->
                        val placeId = r.hitTest(x, y, width, height)
                        if (placeId != null) {
                            onMarkerClicked?.invoke(placeId)
                        } else {
                            notifyCameraChanged()
                        }
                    }
                } else {
                    notifyCameraChanged()
                }
            }
        }
        return true
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

    fun getCameraState(): GlobeCameraState? {
        return renderer?.getCameraState()
    }

    fun updateMarkers(markers: List<GlobeMarker>) {
        queueEvent {
            renderer?.updateMarkers(markers)
            requestRender()
        }
    }

    fun animateTo(latitude: Double, longitude: Double) {
        val r = renderer ?: return
        animator?.cancel()

        val startAngleX = r.angleX
        val startAngleY = r.angleY

        val targetAngleX = latitude.toFloat().coerceIn(-90f, 90f)
        
        // Find shortest path for Y rotation
        var diffY = (longitude.toFloat() - startAngleY) % 360f
        if (diffY > 180f) diffY -= 360f
        if (diffY < -180f) diffY += 360f
        val targetAngleY = startAngleY + diffY

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                r.angleX = startAngleX + (targetAngleX - startAngleX) * fraction
                r.angleY = startAngleY + (targetAngleY - startAngleY) * fraction
                requestRender()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    notifyCameraChanged()
                }
            })
            start()
        }
    }
}
