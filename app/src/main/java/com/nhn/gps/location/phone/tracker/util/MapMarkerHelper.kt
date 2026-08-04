package com.nhn.gps.location.phone.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.databinding.LayoutCustomMarkerBinding

object MapMarkerHelper {

    enum class MarkerStyle { DEFAULT, GLOW }

    fun updateMarkerIcon(
        context: Context,
        marker: Marker,
        avatarUrl: String?,
        style: MarkerStyle = MarkerStyle.GLOW
    ) {
        if (avatarUrl.isNullOrEmpty() || avatarUrl == "null") {
            val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, null, style)
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
            marker.setAnchor(0.5f, anchorY)
            return
        }

        Glide.with(context)
            .asBitmap()
            .load(avatarUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, resource, style)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    marker.setAnchor(0.5f, anchorY)
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // Not needed
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, null, style)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    marker.setAnchor(0.5f, anchorY)
                }
            })
    }

    fun createDefaultMarkerBitmap(context: Context, style: MarkerStyle = MarkerStyle.GLOW): Bitmap {
        return createMarkerBitmapWithAnchor(context, null, style).first
    }

    /**
     * Returns a pair of Bitmap and the calculated vertical anchor (0.0 to 1.0)
     * so that the marker points accurately to the GPS location regardless of the glow.
     */
    private fun createMarkerBitmapWithAnchor(context: Context, avatarBitmap: Bitmap?, style: MarkerStyle): Pair<Bitmap, Float> {
        val markerViewBinding = LayoutCustomMarkerBinding.inflate(LayoutInflater.from(context))
        markerViewBinding.imgGlow.visibility = if (style == MarkerStyle.GLOW) View.VISIBLE else View.GONE
        
        // Adjust translationY based on style
        val density = context.resources.displayMetrics.density
        if (style == MarkerStyle.GLOW) {
            markerViewBinding.imgAvatar.translationY = 36f * density
        } else {
            markerViewBinding.imgAvatar.translationY = 4.5f * density
        }
        
        if (avatarBitmap != null) {
            markerViewBinding.imgAvatar.setImageBitmap(avatarBitmap)
        } else {
            markerViewBinding.imgAvatar.setImageResource(R.drawable.ic_avt_location)
        }
        
        // Measure the view to get actual dimensions
        val view = markerViewBinding.root
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        
        val bitmap = MapUtils.createBitmapFromView(view)
        
        // Calculate anchor
        // The GPS point should be at the bottom tip of the pin.
        // In the layout, imgPin is centered in a 120dp container (if GLOW) or its own size (if DEFAULT).
        // Since both imgPin and imgGlow have layout_gravity="center", their centers overlap.
        
        val totalHeight = view.measuredHeight.toFloat()
        val pinHeight = markerViewBinding.imgPin.measuredHeight.toFloat()
        
        // Vertical center of the container
        val centerY = totalHeight / 2f
        
        // Bottom tip of the pin relative to the top of the bitmap:
        // center + (pinHeight / 2)
        val tipY = centerY + (pinHeight / 2f)
        
        // Anchor is a fraction of the total height
        val anchorY = if (totalHeight > 0) tipY / totalHeight else 1.0f
        
        return Pair(bitmap, anchorY)
    }
}
