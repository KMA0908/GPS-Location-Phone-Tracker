package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal const val FAMOUS_PLACE_ASSET_DIRECTORY = "famous_places_images"
private val assetAvailability = ConcurrentHashMap<String, Boolean>()

internal fun selectFirstAvailableFamousPlacePhoto(
    photoNames: List<String>,
    assetExists: (String) -> Boolean,
): String? = photoNames.firstOrNull(assetExists)

internal fun Context.firstAvailableFamousPlacePhoto(place: FamousPlaceModel): String? =
    selectFirstAvailableFamousPlacePhoto(place.previewPhotos) { fileName ->
        val assetPath = "$FAMOUS_PLACE_ASSET_DIRECTORY/$fileName"
        assetAvailability.getOrPut(assetPath) {
            try {
                assets.open(assetPath).use { }
                true
            } catch (_: IOException) {
                false
            }
        }
    }

/** Loads verified packaged artwork first and falls back to the existing category image. */
fun ImageView.loadFamousPlaceImage(place: FamousPlaceModel) {
    val fallback = place.imageRes.takeIf { it != 0 } ?: R.drawable.ic_paris
    val assetUri = context.firstAvailableFamousPlacePhoto(place)?.let {
        "file:///android_asset/$FAMOUS_PLACE_ASSET_DIRECTORY/$it"
    }

    Glide.with(this)
        .load(assetUri ?: fallback)
        .placeholder(fallback)
        .error(fallback)
        .centerCrop()
        .into(this)
}
