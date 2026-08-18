package com.nhn.gps.location.phone.tracker.ui.explore

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel

/** Loads the packaged category artwork used by the reference app when remote photos are absent. */
fun ImageView.loadFamousPlaceImage(place: FamousPlaceModel) {
    val image = place.imageRes.takeIf { it != 0 } ?: R.drawable.ic_paris
    Glide.with(this)
        .load(image)
        .placeholder(image)
        .error(image)
        .centerCrop()
        .into(this)
}
