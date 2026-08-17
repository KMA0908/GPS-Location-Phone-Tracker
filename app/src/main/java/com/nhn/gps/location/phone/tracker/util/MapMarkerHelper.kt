package com.nhn.gps.location.phone.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
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
        avatarKey: String?,
        avatarUrl: String?,
        style: MarkerStyle = MarkerStyle.GLOW,
        label: String? = null
    ) {
        val localRes = AvatarHelper.getDrawableRes(avatarKey)
        if (localRes != null) {
            val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, null, style, localRes, label)
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
            marker.setAnchor(0.5f, anchorY)
            return
        }

        if (avatarUrl.isNullOrEmpty() || avatarUrl == "null") {
            val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, null, style, label = label)
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
                    // Check if marker is still valid before updating icon (prevent crash if removed)
                    try {
                        val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, resource, style, label = label)
                        marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        marker.setAnchor(0.5f, anchorY)
                    } catch (e: Exception) {
                        // Marker might be removed
                    }
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // Not needed
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    try {
                        val (bitmap, anchorY) = createMarkerBitmapWithAnchor(context, null, style, label = label)
                        marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        marker.setAnchor(0.5f, anchorY)
                    } catch (e: Exception) {
                    }
                }
            })
    }

    fun createDefaultMarkerBitmap(
        context: Context, 
        style: MarkerStyle = MarkerStyle.GLOW,
        label: String? = null
    ): Bitmap {
        return createMarkerBitmapWithAnchor(context, null, style, label = label).first
    }

    /**
     * Returns a pair of Bitmap and the calculated vertical anchor (0.0 to 1.0)
     * so that the marker points accurately to the GPS location regardless of the glow or label.
     */
    private fun createMarkerBitmapWithAnchor(
        context: Context, 
        avatarBitmap: Bitmap?, 
        style: MarkerStyle,
        @DrawableRes localAvatarRes: Int? = null,
        label: String? = null
    ): Pair<Bitmap, Float> {
        val markerViewBinding = LayoutCustomMarkerBinding.inflate(LayoutInflater.from(context))
        markerViewBinding.imgGlow.visibility = if (style == MarkerStyle.GLOW) View.VISIBLE else View.GONE
        
        // Adjust translationY based on style
        val density = context.resources.displayMetrics.density
        if (style == MarkerStyle.GLOW) {
            markerViewBinding.imgAvatar.translationY = 36f * density
        } else {
            markerViewBinding.imgAvatar.translationY = 4.5f * density
        }
        
        // Set label
        if (!label.isNullOrBlank()) {
            markerViewBinding.tvMarkerName.text = label
            markerViewBinding.tvMarkerName.visibility = View.VISIBLE
        } else {
            markerViewBinding.tvMarkerName.visibility = View.GONE
        }

        if (avatarBitmap != null) {
            markerViewBinding.imgAvatar.setImageBitmap(avatarBitmap)
        } else if (localAvatarRes != null) {
            markerViewBinding.imgAvatar.setImageResource(localAvatarRes)
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
        
        // Total height of the bitmap (including TextView if visible)
        val totalHeight = view.measuredHeight.toFloat()
        
        // Vertical center of the icon container (imgGlow is the reference for centering)
        // If style is GLOW, container height is 120dp.
        // If style is DEFAULT, container height is 54dp.
        val containerHeight = markerViewBinding.markerIconContainer.measuredHeight.toFloat()
        val pinHeight = markerViewBinding.imgPin.measuredHeight.toFloat()
        
        // Center of the pin within its container
        val centerY = containerHeight / 2f
        
        // Bottom tip of the pin relative to the top of the container:
        // center + (pinHeight / 2)
        val tipYInContainer = centerY + (pinHeight / 2f)
        
        // Anchor is tip position divided by total height
        val anchorY = if (totalHeight > 0) tipYInContainer / totalHeight else 1.0f
        
        return Pair(bitmap, anchorY)
    }
}
