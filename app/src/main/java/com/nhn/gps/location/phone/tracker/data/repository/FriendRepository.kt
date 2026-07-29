package com.nhn.gps.location.phone.tracker.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface FriendRepository {
    fun getFriends(userId: String): Flow<List<FriendLocation>>
    suspend fun findFriendById(friendId: String): Result<FriendLocation>
    suspend fun addFriend(currentUserId: String, currentUserName: String, friend: FriendLocation): Result<Unit>
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit>
}

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase
) : FriendRepository {

    // Note: The prompt suggests a flat /friends structure where everyone's UID is a key.
    // If we still want a per-user friend list for filtering:
    private val usersRef = database.getReference("users")
    private val globalFriendsRef = database.getReference("friends")

    override fun getFriends(userId: String): Flow<List<FriendLocation>> = callbackFlow {
        // Listening to the user's specific friends list
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friends = mutableListOf<FriendLocation>()
                for (child in snapshot.children) {
                    child.getValue(FriendLocation::class.java)?.let { friend ->
                        friend.id = child.key ?: ""
                        friends.add(friend)
                    }
                }
                Log.d("FriendDebug", "FriendRepository emit size = ${friends.size}")
                trySend(friends)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        val ref = usersRef.child(userId).child("friends")
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun findFriendById(friendId: String): Result<FriendLocation> = withContext(Dispatchers.IO) {
        try {
            // Find user info in the global friends table (everyone's latest info)
            val snapshot = globalFriendsRef.child(friendId).get().await()
            if (snapshot.exists()) {
                val friend = snapshot.getValue(FriendLocation::class.java)
                if (friend != null) {
                    friend.id = friendId
                    Result.success(friend)
                } else {
                    Result.failure(Exception("Parse error"))
                }
            } else {
                Result.failure(Exception("Friend not found"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun addFriend(
        currentUserId: String,
        currentUserName: String,
        friend: FriendLocation
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Add to my friends list
            usersRef.child(currentUserId).child("friends").child(friend.id).setValue(friend).await()

            // 2. Get my info from global table
            val mySnapshot = globalFriendsRef.child(currentUserId).get().await()
            val myInfo = mySnapshot.getValue(FriendLocation::class.java) ?: FriendLocation(
                id = currentUserId,
                name = currentUserName
            )
            myInfo.id = currentUserId

            // 3. Add me to their friends list
            usersRef.child(friend.id).child("friends").child(currentUserId).setValue(myInfo).await()

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            usersRef.child(currentUserId).child("friends").child(friendId).removeValue().await()
            usersRef.child(friendId).child("friends").child(currentUserId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
