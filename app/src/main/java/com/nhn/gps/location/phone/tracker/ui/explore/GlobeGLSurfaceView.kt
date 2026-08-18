package com.nhn.gps.location.phone.tracker.ui.explore

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import earth.worldwind.BasicWorldWindowController
import earth.worldwind.PickedObjectList
import earth.worldwind.WorldWindow
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.Angle
import earth.worldwind.geom.LookAt
import earth.worldwind.geom.Position
import earth.worldwind.globe.elevation.coverage.BasicElevationCoverage
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.layer.mercator.WebMercatorLayerFactory
import earth.worldwind.layer.starfield.StarFieldLayer
import earth.worldwind.navigator.NavigatorAction
import earth.worldwind.navigator.NavigatorEvent
import earth.worldwind.navigator.NavigatorListener
import earth.worldwind.render.image.ImageSource
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.PlacemarkAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.ln

/**
 * WorldWind backed globe used by the decompiled reference app.
 *
 * The previous implementation projected one static bitmap onto a custom sphere. That looked
 * acceptable only from one zoom level. WorldWind gives us the same high-resolution Google
 * satellite tile pyramid, real star field, atmosphere and terrain navigation as the reference.
 */
class GlobeGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worldWindow = WorldWindow(context)
    private val placesLayer = RenderableLayer("Famous places")
    private val lookAt = LookAt()
    private var cameraAnimator: ValueAnimator? = null
    private var lastPickRequest: Deferred<PickedObjectList>? = null
    private val markerImageCache = mutableMapOf<Long, ImageSource>()

    val isSupported: Boolean = true

    var onCameraChanged: ((GlobeCameraState) -> Unit)? = null
    var onMarkerClicked: ((String) -> Unit)? = null

    private val navigatorListener = object : NavigatorListener {
        override fun onNavigatorEvent(wwd: WorldWindow, event: NavigatorEvent) {
            if (event.action == NavigatorAction.STOPPED) notifyCameraChanged()
        }
    }

    init {
        setBackgroundColor(Color.rgb(2, 7, 20))
        addView(
            worldWindow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        setupReferenceLayers()
        worldWindow.controller = PlacePickController(worldWindow)
        worldWindow.navigatorEvents.addNavigatorListener(navigatorListener)

        post {
            showWholeGlobe(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, animate = false)
            notifyCameraChanged()
        }
    }

    private fun setupReferenceLayers() {
        val satelliteLayer = WebMercatorLayerFactory.createLayer(
            urlTemplate = GOOGLE_SATELLITE_URL,
            imageFormat = "image/jpeg",
            name = "Google Satellite",
        )
        worldWindow.engine.layers.apply {
            addLayer(BackgroundLayer())
            addLayer(satelliteLayer)
            addLayer(StarFieldLayer())
            addLayer(AtmosphereLayer())
            addLayer(placesLayer)
        }
        worldWindow.engine.globe.elevationModel.addCoverage(BasicElevationCoverage())
    }

    fun getCameraState(): GlobeCameraState {
        worldWindow.engine.cameraAsLookAt(lookAt)
        return GlobeCameraState(
            centerLatitude = lookAt.position.latitude.inDegrees,
            centerLongitude = lookAt.position.longitude.inDegrees,
            cameraDistance = ln(lookAt.range.coerceAtLeast(1.0)).toFloat(),
        )
    }

    fun updateMarkers(markers: List<GlobeMarker>) {
        placesLayer.clearRenderables()
        markers.forEach { marker ->
            val imageSource = markerImageSource(marker.imageRes, marker.isSelected)
            val attributes = PlacemarkAttributes.createWithImage(imageSource).apply {
                imageScale = 1.0
                imageOffset = earth.worldwind.geom.Offset.bottomCenter()
                isDepthTest = false
            }
            placesLayer.addRenderable(
                Placemark(
                    Position.fromDegrees(marker.latitude, marker.longitude, 0.0),
                    attributes,
                ).apply {
                    displayName = marker.id
                    altitudeMode = AltitudeMode.CLAMP_TO_GROUND
                    isAlwaysOnTop = true
                    isEyeDistanceScaling = false
                    highlightAttributes = PlacemarkAttributes(attributes).apply {
                        imageScale = 1.0
                    }
                    isHighlighted = marker.isSelected
                },
            )
        }
        worldWindow.requestRedraw()
    }

    private fun markerImageSource(imageRes: Int, selected: Boolean): ImageSource {
        val safeImageRes = imageRes.takeIf { it != 0 }
            ?: com.nhn.gps.location.phone.tracker.R.drawable.place_category_1
        val key = (safeImageRes.toLong() shl 1) or if (selected) 1L else 0L
        return markerImageCache.getOrPut(key) {
            ImageSource.fromBitmap(createPhotoMarkerBitmap(safeImageRes, selected))
        }
    }

    private fun createPhotoMarkerBitmap(imageRes: Int, selected: Boolean): Bitmap {
        val density = resources.displayMetrics.density
        val size = ((if (selected) 52f else 46f) * density).toInt().coerceAtLeast(1)
        val border = (if (selected) 3.5f else 2.5f) * density
        val radius = 10f * density
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outer = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(outer, radius, radius, paint)

        val inner = RectF(border, border, size - border, size - border)
        val path = Path().apply {
            addRoundRect(inner, radius - border, radius - border, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        context.getDrawable(imageRes)?.mutate()?.apply {
            setBounds(inner.left.toInt(), inner.top.toInt(), inner.right.toInt(), inner.bottom.toInt())
            draw(canvas)
        }
        canvas.restore()
        return bitmap
    }

    /** Matches the reference selection transition: fly to a place and reveal satellite detail. */
    fun animateTo(latitude: Double, longitude: Double) {
        animateLookAt(latitude, longitude, PLACE_DETAIL_RANGE, PLACE_ANIMATION_DURATION)
    }

    /** Centers a location while retaining the complete-globe composition used on first load. */
    fun showWholeGlobe(latitude: Double, longitude: Double, animate: Boolean = true) {
        val range = overviewRange()
        if (animate) {
            animateLookAt(latitude, longitude, range, OVERVIEW_ANIMATION_DURATION)
        } else {
            applyLookAt(latitude, longitude, range)
            worldWindow.requestRedraw()
        }
    }

    fun resetView() {
        showWholeGlobe(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
    }

    private fun animateLookAt(
        latitude: Double,
        longitude: Double,
        targetRange: Double,
        durationMillis: Long,
    ) {
        cameraAnimator?.cancel()
        worldWindow.engine.cameraAsLookAt(lookAt)

        val startLatitude = lookAt.position.latitude.inDegrees
        val startLongitude = lookAt.position.longitude.inDegrees
        val startRange = lookAt.range.coerceAtLeast(10.0)
        var longitudeDelta = (longitude - startLongitude) % 360.0
        if (longitudeDelta > 180.0) longitudeDelta -= 360.0
        if (longitudeDelta < -180.0) longitudeDelta += 360.0

        cameraAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = (animation.animatedValue as Float).toDouble()
                applyLookAt(
                    latitude = startLatitude + (latitude - startLatitude) * fraction,
                    longitude = startLongitude + longitudeDelta * fraction,
                    range = startRange + (targetRange - startRange) * fraction,
                )
                worldWindow.requestRedraw()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    notifyCameraChanged()
                }
            })
            start()
        }
    }

    private fun applyLookAt(latitude: Double, longitude: Double, range: Double) {
        lookAt.position.setDegrees(latitude.coerceIn(-90.0, 90.0), longitude, 0.0)
        lookAt.altitudeMode = AltitudeMode.ABSOLUTE
        lookAt.range = range.coerceIn(MIN_RANGE, MAX_RANGE)
        lookAt.heading = Angle.ZERO
        lookAt.tilt = Angle.ZERO
        lookAt.roll = Angle.ZERO
        worldWindow.engine.cameraFromLookAt(lookAt)
    }

    private fun wholeGlobeRange(): Double =
        worldWindow.engine.distanceToViewGlobeExtents.coerceAtLeast(FALLBACK_GLOBE_RANGE)

    private fun overviewRange(): Double = wholeGlobeRange() * OVERVIEW_DISTANCE_SCALE

    private fun notifyCameraChanged() {
        onCameraChanged?.invoke(getCameraState())
    }

    fun onResume() {
        worldWindow.onResume()
    }

    fun onPause() {
        worldWindow.onPause()
    }

    fun release() {
        cameraAnimator?.cancel()
        cameraAnimator = null
        worldWindow.navigatorEvents.removeNavigatorListener(navigatorListener)
        worldWindow.onPause()
        viewScope.cancel()
        onCameraChanged = null
        onMarkerClicked = null
        removeView(worldWindow)
    }

    private inner class PlacePickController(wwd: WorldWindow) : BasicWorldWindowController(wwd) {
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                lastPickRequest = worldWindow.pickAsync(event.x, event.y)
                return false
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val request = lastPickRequest ?: return false
                viewScope.launch {
                    val placemark = request.await().topPickedObject?.userObject as? Placemark
                    placemark?.displayName?.let { onMarkerClicked?.invoke(it) }
                }
                return false
            }
        })

        override fun onTouchEvent(event: MotionEvent): Boolean =
            if (!detector.onTouchEvent(event)) super.onTouchEvent(event) else true
    }

    companion object {
        private const val GOOGLE_SATELLITE_URL =
            "https://mt.google.com/vt/lyrs=s&x={x}&y={y}&z={z}&hl={lang}"
        private const val DEFAULT_LATITUDE = 20.0
        private const val DEFAULT_LONGITUDE = 0.0
        private const val PLACE_DETAIL_RANGE = 10_000.0
        private const val MIN_RANGE = 1_000.0
        private const val MAX_RANGE = 50_000_000.0
        private const val FALLBACK_GLOBE_RANGE = 12_000_000.0
        private const val OVERVIEW_DISTANCE_SCALE = 1.35
        private const val PLACE_ANIMATION_DURATION = 1_200L
        private const val OVERVIEW_ANIMATION_DURATION = 1_000L
    }
}
