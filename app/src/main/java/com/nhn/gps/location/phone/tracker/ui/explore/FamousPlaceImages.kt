package com.nhn.gps.location.phone.tracker.ui.explore

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val FAMOUS_PLACE_ASSET_DIRECTORY = "famous_places_images"
private val assetAvailability = ConcurrentHashMap<String, Boolean>()

internal fun selectFirstAvailableFamousPlacePhoto(
    photoNames: List<String>,
    assetExists: (String) -> Boolean,
): String? = photoNames.firstOrNull(assetExists)

private fun ImageView.firstAvailablePhoto(place: FamousPlaceModel): String? =
    selectFirstAvailableFamousPlacePhoto(place.previewPhotos) { fileName ->
        val assetPath = "$FAMOUS_PLACE_ASSET_DIRECTORY/$fileName"
        assetAvailability.getOrPut(assetPath) {
            try {
                context.assets.open(assetPath).use { }
                true
            } catch (_: IOException) {
                false
            }
        }
    }

/** Loads verified packaged artwork first and falls back to the existing category image. */
fun ImageView.loadFamousPlaceImage(place: FamousPlaceModel) {
    val fallback = place.imageRes.takeIf { it != 0 } ?: R.drawable.ic_paris
    val assetUri = firstAvailablePhoto(place)?.let {
        "file:///android_asset/$FAMOUS_PLACE_ASSET_DIRECTORY/$it"
    }

    Glide.with(this)
        .load(assetUri ?: fallback)
        .placeholder(fallback)
        .error(fallback)
        .centerCrop()
        .into(this)
}
