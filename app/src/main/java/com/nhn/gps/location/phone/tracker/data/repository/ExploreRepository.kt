package com.nhn.gps.location.phone.tracker.data.repository

import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import kotlinx.coroutines.flow.Flow

sealed interface ExploreResult<out T> {
    data class Success<T>(val data: T) : ExploreResult<T>
    data object Empty : ExploreResult<Nothing>
    data object NoNetwork : ExploreResult<Nothing>
    data object InvalidPlace : ExploreResult<Nothing>
    data class ApiError(val message: String?) : ExploreResult<Nothing>
    data class Unknown(val throwable: Throwable) : ExploreResult<Nothing>
}

interface ExploreRepository {
    suspend fun getNearbyFamousPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        maxResults: Int = 4,
        includedTypes: List<String> = listOf("tourist_attraction")
    ): ExploreResult<List<FamousPlaceModel>>

    suspend fun getPlaceDetail(placeId: String): ExploreResult<FamousPlaceModel>

    suspend fun searchPlaces(query: String): ExploreResult<List<FamousPlaceModel>>

    suspend fun getAllFamousPlaces(): ExploreResult<List<FamousPlaceModel>>

    fun observeFavoritePlaceIds(): Flow<Set<String>>

    suspend fun toggleFavorite(placeId: String)
}
