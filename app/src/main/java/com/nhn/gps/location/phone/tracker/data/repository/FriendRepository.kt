package com.nhn.gps.location.phone.tracker.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface FriendRepository {
    fun getFriends(userId: String): Flow<List<FriendLocation>>
    suspend fun findFriendById(friendId: String): Result<UserProfile>
    suspend fun addFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit>
}

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase
) : FriendRepository {

    private val usersRef = database.getReference("users")

    private fun getFriendUids(userId: String): Flow<Set<String>> = callbackFlow {
        Log.d("FriendRepo", "Listening to friend UIDs for $userId")
        val ref = usersRef.child(userId).child("friends")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uids = snapshot.children.mapNotNull { it.key }.toSet()
                Log.d("FriendRepo", "Friend UIDs updated: $uids")
                trySend(uids)
            }
            override fun onCancelled(error: DatabaseError) { 
                Log.e("FriendRepo", "Friend UIDs error", error.toException())
                close(error.toException()) 
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun getAllUsersRaw(): Flow<DataSnapshot> = callbackFlow {
        Log.d("FriendRepo", "Listening to all users for data sync")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }
            override fun onCancelled(error: DatabaseError) { 
                Log.e("FriendRepo", "All users listener error", error.toException())
                close(error.toException()) 
            }
        }
        usersRef.addValueEventListener(listener)
        awaitClose { usersRef.removeEventListener(listener) }
    }

    override fun getFriends(userId: String): Flow<List<FriendLocation>> = 
        getFriendUids(userId).combine(getAllUsersRaw()) { uids, usersSnapshot ->
            Log.d("FriendRepo", "Combining UIDs with user data. UIDs size: ${uids.size}")
            uids.mapNotNull { uid ->
                val userNode = usersSnapshot.child(uid)
                val profile = userNode.child("profile").getValue(UserProfile::class.java)
                val loc = userNode.child("location").getValue(UserLocation::class.java)
                
                if (profile != null) {
                    FriendLocation(
                        id = uid,
                        name = profile.name,
                        avatarUrl = profile.avatarUrl,
                        latitude = loc?.latitude ?: 0.0,
                        longitude = loc?.longitude ?: 0.0,
                        updatedAt = loc?.updatedAt ?: 0L
                    )
                } else {
                    Log.w("FriendRepo", "Profile not found for friend UID: $uid")
                    null
                }
            }
        }

    override suspend fun findFriendById(friendId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d("FriendRepo", "Finding friend by ID: $friendId")
            val snapshot = usersRef.child(friendId).child("profile").get().await()
            if (snapshot.exists()) {
                val profile = snapshot.getValue(UserProfile::class.java)
                if (profile != null) {
                    // Đảm bảo UID trong object trả về khớp với ID node được yêu cầu
                    Result.success(profile.copy(uid = friendId))
                } else {
                    Result.failure(Exception("Parse error"))
                }
            } else {
                Result.failure(Exception("Friend not found"))
            }
        } catch (e: Exception) {
            Log.e("FriendRepo", "Error finding friend", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun addFriend(
        currentUserId: String,
        friendId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("FriendRepo", "Adding friend: myUid=$currentUserId, friendId=$friendId")
            // users/{myUid}/friends/{friendUid} = true
            usersRef.child(currentUserId).child("friends").child(friendId).setValue(true).await()
            // users/{friendUid}/friends/{myUid} = true
            usersRef.child(friendId).child("friends").child(currentUserId).setValue(true).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendRepo", "Error adding friend", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("FriendRepo", "Removing friend: myUid=$currentUserId, friendId=$friendId")
            usersRef.child(currentUserId).child("friends").child(friendId).removeValue().await()
            usersRef.child(friendId).child("friends").child(currentUserId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendRepo", "Error removing friend", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
