package com.nhn.gps.location.phone.tracker.data.repository

import android.content.Context
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import kotlin.math.*

class ExploreRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) : ExploreRepository {

    private var cachedFamousPlaces: List<FamousPlaceModel>? = null

    private suspend fun loadFamousPlacesFromJson(): List<FamousPlaceModel> = withContext(Dispatchers.IO) {
        cachedFamousPlaces?.let { return@withContext it }
        try {
            val jsonString = context.assets.open("famous_places.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<FamousPlaceModel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val previewPhotos = mutableListOf<String>()
                val photosArray = obj.optJSONArray("previewPhotos")
                if (photosArray != null) {
                    for (j in 0 until photosArray.length()) {
                        previewPhotos.add(photosArray.getString(j))
                    }
                }
                
                val idInt = obj.getInt("id")
                val type = obj.getInt("idPlaceType")
                val lat = obj.getDouble("latitude")
                val lng = obj.getDouble("longitude")
                
                list.add(
                    FamousPlaceModel(
                        id = idInt.toString(),
                        name = obj.getString("placeName"),
                        location = obj.optString("address", ""),
                        imageRes = mapTypeToImage(type),
                        rating = obj.optDouble("rating", 0.0).toFloat(),
                        reviewCount = obj.optInt("reviewCount", 0),
                        distanceKm = null,
                        category = mapTypeToCategory(type),
                        latitude = lat,
                        longitude = lng,
                        idPlaceType = type,
                        descriptionResKey = obj.optString("descriptionResKey"),
                        previewPhotos = previewPhotos,
                        address = obj.optString("address").takeIf { it.isNotBlank() },
                        isOpen = if (obj.has("isOpen")) obj.getBoolean("isOpen") else null,
                        elevationMeters = if (obj.has("elevationMeters")) obj.getDouble("elevationMeters") else null,
                        estimatedVisitMinutes = if (obj.has("estimatedVisitMinutes")) obj.getInt("estimatedVisitMinutes") else null,
                        reviewCountLong = obj.optLong("reviewCount").takeIf { obj.has("reviewCount") },
                        description = obj.optString("description").takeIf { it.isNotBlank() },
                        imageAttribution = obj.optString("imageAttribution").takeIf { it.isNotBlank() },
                        websiteUri = obj.optString("websiteUri").takeIf { it.isNotBlank() }
                    )
                )
            }
            cachedFamousPlaces = list
            list
        } catch (e: Exception) {
            android.util.Log.e("ExploreRepository", "Error loading JSON", e)
            emptyList()
        }
    }

    private fun mapTypeToCategory(type: Int): String {
        return when (type) {
            1 -> "Romantic"
            2 -> "Theme Parks"
            3 -> "Mountains"
            4 -> "Nature"
            5 -> "Dangerous"
            6 -> "Mysterious"
            7 -> "Surf"
            8 -> "Ghost Towns"
            9 -> "Film Locations"
            10 -> "Extreme Weather"
            11 -> "Family"
            12 -> "Cities"
            13 -> "Clubs"
            14 -> "Nightlife"
            else -> "Famous"
        }
    }

    private fun mapTypeToImage(type: Int): Int = when (type.coerceIn(1, 14)) {
        1 -> R.drawable.place_category_1
        2 -> R.drawable.place_category_2
        3 -> R.drawable.place_category_3
        4 -> R.drawable.place_category_4
        5 -> R.drawable.place_category_5
        6 -> R.drawable.place_category_6
        7 -> R.drawable.place_category_7
        8 -> R.drawable.place_category_8
        9 -> R.drawable.place_category_9
        10 -> R.drawable.place_category_10
        11 -> R.drawable.place_category_11
        12 -> R.drawable.place_category_12
        13 -> R.drawable.place_category_13
        else -> R.drawable.place_category_14
    }

    override suspend fun getNearbyFamousPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        maxResults: Int,
        includedTypes: List<String>
    ): ExploreResult<List<FamousPlaceModel>> {
        val allPlaces = loadFamousPlacesFromJson()
        val nearby = allPlaces.asSequence().filter { 
            calculateDistance(latitude, longitude, it.latitude, it.longitude) <= radiusMeters
        }.take(maxResults).toList()
        
        return if (nearby.isEmpty()) ExploreResult.Empty else ExploreResult.Success(nearby)
    }

    override suspend fun getPlaceDetail(placeId: String): ExploreResult<FamousPlaceModel> {
        val allPlaces = loadFamousPlacesFromJson()
        val place = allPlaces.find { it.id == placeId }
        return if (place != null) ExploreResult.Success(place) else ExploreResult.InvalidPlace
    }

    override suspend fun searchPlaces(query: String): ExploreResult<List<FamousPlaceModel>> {
        val allPlaces = loadFamousPlacesFromJson()
        val results = allPlaces.asSequence().filter { it.name.contains(query, ignoreCase = true) }.take(10).toList()
        return if (results.isEmpty()) ExploreResult.Empty else ExploreResult.Success(results)
    }

    override suspend fun getResolvedPhotoUri(metadata: PhotoMetadata): String? {
        return null
    }

    override suspend fun getAllFamousPlaces(): ExploreResult<List<FamousPlaceModel>> {
        val allPlaces = loadFamousPlacesFromJson()
        return if (allPlaces.isEmpty()) ExploreResult.Empty else ExploreResult.Success(allPlaces)
    }

    override fun observeFavoritePlaceIds(): Flow<Set<String>> {
        return appPreferences.favoritePlacesFlow.map { list ->
            list.map { it.id }.toSet()
        }
    }

    override suspend fun toggleFavorite(placeId: String) {
        appPreferences.toggleFavoritePlace(placeId)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = lat1 * (PI / 180.0)
        val phi2 = lat2 * (PI / 180.0)
        val dPhi = (lat2 - lat1) * (PI / 180.0)
        val dLambda = (lon2 - lon1) * (PI / 180.0)

        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }
}
