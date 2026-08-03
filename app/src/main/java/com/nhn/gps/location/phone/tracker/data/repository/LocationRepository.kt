package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface LocationRepository {
    suspend fun updateSelfLocation(uid: String, location: UserLocation): Result<Unit>
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase
) : LocationRepository {

    private val usersRef = database.getReference("users")

    override suspend fun updateSelfLocation(uid: String, location: UserLocation): Result<Unit> {
        return try {
            // Update only the location node for the specific user: users/{uid}/location
            usersRef.child(uid).child("location").setValue(location).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
