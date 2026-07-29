package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface LocationRepository {
    fun getAllUsersLocations(): Flow<List<FriendLocation>>
    suspend fun updateSelfLocation(uid: String, location: FriendLocation): Result<Unit>
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase
) : LocationRepository {

    private val friendsRef = database.getReference("friends")

    override fun getAllUsersLocations(): Flow<List<FriendLocation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<FriendLocation>()
                for (child in snapshot.children) {
                    child.getValue(FriendLocation::class.java)?.let { location ->
                        location.id = child.key ?: ""
                        locations.add(location)
                    }
                }
                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        friendsRef.addValueEventListener(listener)
        awaitClose { friendsRef.removeEventListener(listener) }
    }

    override suspend fun updateSelfLocation(uid: String, location: FriendLocation): Result<Unit> {
        return try {
            friendsRef.child(uid).setValue(location).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
