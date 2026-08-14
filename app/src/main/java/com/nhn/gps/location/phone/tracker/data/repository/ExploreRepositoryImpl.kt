package com.nhn.gps.location.phone.tracker.data.repository

import android.content.Context
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import kotlin.math.*

class ExploreRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
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
                val lat = obj.getDouble("latitude")
                val lng = obj.getDouble("longitude")
                
                list.add(
                    FamousPlaceModel(
                        id = idInt.toString(),
                        name = obj.getString("placeName"),
                        location = "",
                        imageRes = 0,
                        rating = 0f,
                        reviewCount = 0,
                        distanceKm = 0.0,
                        category = mapTypeToCategory(obj.getInt("idPlaceType")),
                        latitude = lat,
                        longitude = lng,
                        idPlaceType = obj.getInt("idPlaceType"),
                        descriptionResKey = obj.optString("descriptionResKey"),
                        previewPhotos = previewPhotos
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
