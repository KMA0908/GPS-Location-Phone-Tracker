package com.nhn.gps.location.phone.tracker.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.math.ln

class ExploreRepositoryImpl @Inject constructor(
    private val placesClient: PlacesClient
) : ExploreRepository {

    private val placeFields = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.LOCATION,
        Place.Field.LAT_LNG,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.RATING,
        Place.Field.USER_RATING_COUNT,
        Place.Field.PHOTO_METADATAS,
        Place.Field.WEBSITE_URI,
        Place.Field.OPENING_HOURS
    )

    override suspend fun getNearbyFamousPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        maxResults: Int,
        includedTypes: List<String>
    ): ExploreResult<List<FamousPlaceModel>> {
        return try {
            val center = LatLng(latitude, longitude)
            val circle = CircularBounds.newInstance(center, radiusMeters)
            
            val request = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(includedTypes)
                .setMaxResultCount(20) // Get more to sort by popularity
                .build()

            val response = placesClient.searchNearby(request).await()
            val places = response.places
            
            android.util.Log.d("ExploreRepository", "SearchNearby: Found ${places.size} raw places")

            val models = places.asSequence()
                .mapNotNull { place ->
                    if (place.id == null) return@mapNotNull null
                    val coordinate = place.location ?: place.latLng
                    if (coordinate == null) return@mapNotNull null
                    
                    val ratingSafe = (place.rating ?: 0.0)
                    val ratingCountSafe = (place.userRatingCount ?: 0).toDouble()
                    val popularityScore = ratingSafe * ln(ratingCountSafe + 1.0)
                    
                    place to popularityScore
                }
                .sortedByDescending { it.second }
                .take(maxResults)
                .map { (place, _) ->
                    val coordinate = place.location ?: place.latLng
                    FamousPlaceModel(
                        id = place.id!!,
                        name = place.displayName ?: "Unknown",
                        location = place.formattedAddress ?: "",
                        imageRes = 0,
                        rating = (place.rating ?: 0.0).toFloat(),
                        reviewCount = place.userRatingCount ?: 0,
                        distanceKm = 0.0,
                        category = "Famous",
                        isFavorite = false,
                        latitude = coordinate?.latitude ?: 0.0,
                        longitude = coordinate?.longitude ?: 0.0,
                        phoneNumber = null,
                        websiteUri = place.websiteUri?.toString(),
                        isOpen = place.openingHours?.weekdayText?.isNotEmpty(),
                        openingHours = place.openingHours?.weekdayText,
                        types = emptyList(),
                        address = place.formattedAddress,
                        photoMetadata = place.photoMetadatas?.firstOrNull()
                    )
                }
                .toList()

            android.util.Log.d("ExploreRepository", "SearchNearby: Mapped ${models.size} valid places")
            
            if (models.isEmpty()) ExploreResult.Empty else ExploreResult.Success(models)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ExploreRepository", "SearchNearby error", e)
            ExploreResult.ApiError(e.message)
        }
    }

    override suspend fun getPlaceDetail(placeId: String): ExploreResult<FamousPlaceModel> {
        return try {
            val request = FetchPlaceRequest.newInstance(placeId, placeFields)
            val response = placesClient.fetchPlace(request).await()
            val place = response.place
            val coordinate = place.location ?: place.latLng

            if (place.id != null && coordinate != null) {
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
                        isFavorite = false,
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        phoneNumber = null,
                        websiteUri = place.websiteUri?.toString(),
                        isOpen = place.openingHours?.weekdayText?.isNotEmpty(),
                        openingHours = place.openingHours?.weekdayText,
                        types = emptyList(),
                        address = place.formattedAddress,
                        photoMetadata = place.photoMetadatas?.firstOrNull()
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

    override suspend fun searchPlaces(query: String): ExploreResult<List<FamousPlaceModel>> {
        return try {
            val request = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(5)
                .build()
            
            val response = placesClient.searchByText(request).await()
            val places = response.places
            
            val models = places.mapNotNull { place ->
                val coordinate = place.location ?: place.latLng
                if (place.id == null || coordinate == null) return@mapNotNull null
                
                FamousPlaceModel(
                    id = place.id!!,
                    name = place.displayName ?: "Unknown",
                    location = place.formattedAddress ?: "",
                    latitude = coordinate.latitude,
                    longitude = coordinate.longitude,
                    imageRes = 0,
                    rating = (place.rating ?: 0.0).toFloat(),
                    reviewCount = place.userRatingCount ?: 0,
                    distanceKm = 0.0,
                    category = "Search Result",
                    isFavorite = false,
                    phoneNumber = null,
                    websiteUri = place.websiteUri?.toString(),
                    isOpen = place.openingHours?.weekdayText?.isNotEmpty(),
                    openingHours = place.openingHours?.weekdayText,
                    types = emptyList(),
                    address = place.formattedAddress,
                    photoMetadata = place.photoMetadatas?.firstOrNull()
                )
            }
            
            if (models.isEmpty()) ExploreResult.Empty else ExploreResult.Success(models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExploreResult.ApiError(e.message)
        }
    }

    override suspend fun getResolvedPhotoUri(metadata: PhotoMetadata): String? {
        return try {
            val request = FetchResolvedPhotoUriRequest.builder(metadata)
                .build()
            val response = placesClient.fetchResolvedPhotoUri(request).await()
            response.uri?.toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
