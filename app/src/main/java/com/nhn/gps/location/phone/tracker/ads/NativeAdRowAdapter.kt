package com.nhn.gps.location.phone.tracker.ads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.leansoft.ads.view.NativeAdViewContainer
import com.nhn.gps.location.phone.tracker.R

class NativeAdRowAdapter(
    private val placement: String,
    private val format: GpsAdViewBinder.NativeFormat = GpsAdViewBinder.NativeFormat.SMALL,
) : RecyclerView.Adapter<NativeAdRowAdapter.Holder>() {

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = when (format) {
            GpsAdViewBinder.NativeFormat.SMALL -> R.layout.view_ad_native_small
            GpsAdViewBinder.NativeFormat.MEDIUM -> R.layout.view_ad_native_medium
            GpsAdViewBinder.NativeFormat.BIG -> R.layout.view_ad_native_big
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false) as NativeAdViewContainer
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.container.adsPlacement = placement
    }

    class Holder(val container: NativeAdViewContainer) : RecyclerView.ViewHolder(container)
}
