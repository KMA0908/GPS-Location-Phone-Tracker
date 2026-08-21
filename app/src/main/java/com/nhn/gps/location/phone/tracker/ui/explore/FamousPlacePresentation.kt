package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import kotlin.math.roundToInt

internal data class FamousPlacePage(
    val featured: FamousPlaceModel?,
    val trending: List<FamousPlaceModel>,
    val hasMore: Boolean,
)

internal fun paginateFamousPlaces(
    places: List<FamousPlaceModel>,
    visibleCount: Int,
): FamousPlacePage {
    val visible = places.take(visibleCount.coerceAtLeast(0))
    return FamousPlacePage(
        featured = visible.firstOrNull(),
        trending = visible.drop(1),
        hasMore = places.size > visible.size,
    )
}

internal fun filterFamousPlaces(
    places: List<FamousPlaceModel>,
    typeId: Int = 0,
    query: String = "",
): List<FamousPlaceModel> {
    val normalizedQuery = query.trim()
    return places.filter { place ->
        (typeId == 0 || place.idPlaceType == typeId) &&
            (normalizedQuery.isEmpty() || place.name.contains(normalizedQuery, ignoreCase = true))
    }
}

internal fun calculateFamousPlaceDistanceKm(
    userLocation: LatLng?,
    placeLatitude: Double,
    placeLongitude: Double,
): Double? {
    if (userLocation == null ||
        !userLocation.latitude.isValidLatitude() ||
        !userLocation.longitude.isValidLongitude() ||
        !placeLatitude.isValidLatitude() ||
        !placeLongitude.isValidLongitude() ||
        (placeLatitude == 0.0 && placeLongitude == 0.0)
    ) {
        return null
    }

    return SphericalUtil.computeDistanceBetween(
        userLocation,
        LatLng(placeLatitude, placeLongitude),
    ) / METERS_PER_KILOMETER
}

internal fun formatFamousPlaceDistance(context: Context, distanceKm: Double?): String = when {
    distanceKm == null -> context.getString(R.string.distance_unavailable)
    distanceKm < 1.0 -> context.getString(
        R.string.route_distance_meters,
        (distanceKm * METERS_PER_KILOMETER).roundToInt(),
    )
    distanceKm < 10.0 -> context.getString(R.string.route_distance_kilometers_decimal, distanceKm)
    else -> context.getString(R.string.route_distance_kilometers, distanceKm.roundToInt())
}

private fun Double.isValidLatitude(): Boolean = isFinite() && this in -90.0..90.0
private fun Double.isValidLongitude(): Boolean = isFinite() && this in -180.0..180.0

private const val METERS_PER_KILOMETER = 1_000.0
