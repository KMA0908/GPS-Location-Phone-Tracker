package com.nhn.gps.location.phone.tracker.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.max

class ExploreRepositoryImpl @Inject constructor(
    private val placesClient: PlacesClient
) : ExploreRepository {

    private val placeFields = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.LOCATION,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.RATING,
        Place.Field.USER_RATING_COUNT,
        Place.Field.PHOTO_METADATAS
    )

    override suspend fun getNearbyFamousPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        maxResults: Int
    ): ExploreResult<List<FamousPlaceModel>> {
        return try {
            val center = LatLng(latitude, longitude)
            val circle = CircularBounds.newInstance(center, radiusMeters)
            
            val request = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(listOf("tourist_attraction"))
                .setMaxResultCount(20) // Get more to sort by popularity
                .build()

            val response = placesClient.searchNearby(request).await()
            val places = response.places

            val models = places.asSequence()
                .filter { it.id != null && it.location != null }
                .map { place ->
                    val ratingSafe = max(place.rating ?: 0.0, 0.0)
                    val ratingCountSafe = max(place.userRatingCount ?: 0, 0)
                    val popularityScore = ratingSafe * ln(ratingCountSafe + 1.0)
                    
                    place to popularityScore
                }
                .sortedByDescending { it.second }
                .take(maxResults)
                .map { (place, _) ->
                    FamousPlaceModel(
                        id = place.id!!,
                        name = place.displayName ?: "Unknown",
                        location = place.formattedAddress ?: "",
                        imageRes = 0, // Handled in Photo prompt
                        rating = (place.rating ?: 0.0).toFloat(),
                        reviewCount = place.userRatingCount ?: 0,
                        distanceKm = 0.0, // Should be calculated if needed, but not in model currently
                        category = "Famous",
                        isFavorite = false
                    )
                }
                .toList()

            if (models.isEmpty()) ExploreResult.Empty else ExploreResult.Success(models)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExploreResult.ApiError(e.message)
        }
    }

    override suspend fun getPlaceDetail(placeId: String): ExploreResult<FamousPlaceModel> {
        return try {
            val request = FetchPlaceRequest.newInstance(placeId, placeFields)
            val response = placesClient.fetchPlace(request).await()
            val place = response.place

            if (place.id != null && place.location != null) {
                ExploreResult.Success(
                    FamousPlaceModel(
                        id = place.id!!,
                        name = place.displayName ?: "Unknown",
                        location = place.formattedAddress ?: "",
                        imageRes = 0,
                        rating = (place.rating ?: 0.0).toFloat(),
                        reviewCount = place.userRatingCount ?: 0,
                        distanceKm = 0.0,
                        category = "Famous",
                        isFavorite = false
                    )
                )
            } else {
                ExploreResult.InvalidPlace
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExploreResult.ApiError(e.message)
        }
    }
}
