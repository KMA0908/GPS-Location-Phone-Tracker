package com.nhn.gps.location.phone.tracker.data.repository

import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton

interface LocationRepository {
    suspend fun updateSelfLocation(uid: String, location: UserLocation): Result<Unit>
    suspend fun removeSelfLocation(uid: String): Result<Unit>
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val ownerFunctions: OwnerFunctionClient,
) : LocationRepository {

    override suspend fun updateSelfLocation(uid: String, location: UserLocation): Result<Unit> {
        return try {
            FirebasePathKey.requireValid(uid, "Installation ID")
            require(location.latitude.isFinite() && location.latitude in -90.0..90.0) {
                "Invalid latitude"
            }
            require(location.longitude.isFinite() && location.longitude in -180.0..180.0) {
                "Invalid longitude"
            }
            ownerFunctions.call(
                functionName = "updateLocation",
                uid = uid,
                values = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                ),
            )
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun removeSelfLocation(uid: String): Result<Unit> {
        return try {
            FirebasePathKey.requireValid(uid, "Installation ID")
            ownerFunctions.call("removeLocation", uid)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
