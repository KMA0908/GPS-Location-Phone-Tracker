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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface FriendRepository {
    fun getFriends(userId: String): Flow<List<FriendLocation>>
    suspend fun findFriendById(friendId: String): Result<UserProfile>
    suspend fun addFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun isFriend(userId: String, friendId: String): Boolean
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

    private fun observeFriend(friendId: String): Flow<FriendLocation?> {
        val profileFlow = callbackFlow {
            val ref = usersRef.child(friendId).child("profile")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.getValue(UserProfile::class.java))
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

        val locationFlow = callbackFlow {
            val ref = usersRef.child(friendId).child("location")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.getValue(UserLocation::class.java))
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

        return combine(profileFlow, locationFlow) { profile, loc ->
            if (profile != null) {
                FriendLocation(
                    id = friendId,
                    name = profile.name,
                    avatarUrl = profile.avatarUrl,
                    avatarKey = profile.avatarKey,
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                    updatedAt = loc?.updatedAt ?: 0L
                )
            } else {
                null
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getFriends(userId: String): Flow<List<FriendLocation>> = 
        getFriendUids(userId).flatMapLatest { uids ->
            Log.d("FriendRepo", "Processing UIDs for surgical observe: ${uids.size}")
            if (uids.isEmpty()) {
                flowOf(emptyList())
            } else {
                val flows = uids.map { observeFriend(it) }
                combine(flows) { array ->
                    array.filterNotNull().toList()
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
            Log.d("FriendRepo", "Adding friend atomic: myUid=$currentUserId, friendId=$friendId")
            
            val updates = mapOf<String, Any>(
                "$currentUserId/friends/$friendId" to true,
                "$friendId/friends/$currentUserId" to true
            )
            
            usersRef.updateChildren(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendRepo", "Error adding friend atomic", e)
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

    override suspend fun isFriend(userId: String, friendId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshot = usersRef.child(userId).child("friends").child(friendId).get().await()
            snapshot.exists() && snapshot.value == true
        } catch (e: Exception) {
            false
        }
    }
}
