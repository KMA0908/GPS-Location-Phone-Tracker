package com.nhn.gps.location.phone.tracker.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.ui.permission.LocationPermissionBottomSheet.Companion.TAG
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

interface LocationRepository {
    fun getFriendsLocations(): Flow<List<FriendLocation>>
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : LocationRepository {

    override fun getFriendsLocations(): Flow<List<FriendLocation>> =
        callbackFlow {

            val listener = firestore
                .collection("friends")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    snapshot?.let {
                        trySend(it.toObjects(FriendLocation::class.java))
                            .onFailure {
                                Log.w(TAG, "Cannot send friends update", it)
                            }
                    }
                }

            awaitClose {
                listener.remove()
            }

        }
            .distinctUntilChanged()
}
