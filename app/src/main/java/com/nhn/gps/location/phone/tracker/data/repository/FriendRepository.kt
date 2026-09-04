package com.nhn.gps.location.phone.tracker.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.nhn.gps.location.phone.tracker.data.local.InstallationIdentity
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

interface FriendRepository {
    fun getFriends(userId: String): Flow<List<FriendLocation>>
    suspend fun findFriendById(friendId: String): Result<UserProfile>
    suspend fun addFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit>
    suspend fun isFriend(userId: String, friendId: String): Boolean
}

class FriendLimitReachedException(
    val limit: Int,
) : IllegalStateException("Friend limit reached")

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val ownerFunctions: OwnerFunctionClient,
    private val installationIdentity: InstallationIdentity,
) : FriendRepository {

    override fun getFriends(userId: String): Flow<List<FriendLocation>> = flow {
        FirebasePathKey.requireValid(userId, "Current user ID")
        var hasEmitted = false
        while (currentCoroutineContext().isActive) {
            try {
                emit(fetchFriends(userId))
                hasEmitted = true
                delay(FRIEND_REFRESH_INTERVAL_MS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "friend_snapshot_failed", error)
                if (!hasEmitted) {
                    emit(emptyList())
                    hasEmitted = true
                }
                delay(FRIEND_RETRY_INTERVAL_MS)
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun findFriendById(friendId: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val requestedFriendId = friendId.trim()
                FirebasePathKey.requireValid(requestedFriendId, "Friend ID")
                val data = ownerFunctions.call(
                    functionName = "findUserById",
                    uid = installationIdentity.getOrCreateId(),
                    values = mapOf("friendId" to requestedFriendId),
                ).asMap("Invalid profile lookup response")
                val profile = data["profile"].asMap("Invalid profile response")
                    .toUserProfile()
                require(profile.uid.isNotBlank()) { "Friend not found" }
                Result.success(profile)
            } catch (error: Exception) {
                Log.e(TAG, "friend_lookup_failed", error)
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }

    override suspend fun addFriend(
        currentUserId: String,
        friendId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentId = currentUserId.trim()
            val targetFriendId = friendId.trim()
            FirebasePathKey.requireValid(currentId, "Current user ID")
            FirebasePathKey.requireValid(targetFriendId, "Friend ID")
            require(currentId != targetFriendId) {
                "Cannot add the current user as a friend"
            }

            ownerFunctions.call(
                functionName = "addFriend",
                uid = currentId,
                values = mapOf("friendId" to targetFriendId),
            )
            Result.success(Unit)
        } catch (error: Exception) {
            Log.e(TAG, "friend_add_failed", error)
            if (error is CancellationException) throw error
            Result.failure(mapFriendMutationError(error))
        }
    }

    override suspend fun removeFriend(
        currentUserId: String,
        friendId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentId = currentUserId.trim()
            val targetFriendId = friendId.trim()
            FirebasePathKey.requireValid(currentId, "Current user ID")
            FirebasePathKey.requireValid(targetFriendId, "Friend ID")
            ownerFunctions.call(
                functionName = "removeFriend",
                uid = currentId,
                values = mapOf("friendId" to targetFriendId),
            )
            Result.success(Unit)
        } catch (error: Exception) {
            Log.e(TAG, "friend_remove_failed", error)
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    override suspend fun isFriend(userId: String, friendId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val currentId = userId.trim()
                val targetFriendId = friendId.trim()
                if (!FirebasePathKey.isValid(currentId) ||
                    !FirebasePathKey.isValid(targetFriendId)
                ) {
                    return@withContext false
                }
                val data = ownerFunctions.call(
                    functionName = "isFriend",
                    uid = currentId,
                    values = mapOf("friendId" to targetFriendId),
                ).asMap("Invalid friendship response")
                data["isFriend"] == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "friendship_check_failed", error)
                false
            }
        }

    private suspend fun fetchFriends(userId: String): List<FriendLocation> {
        val data = ownerFunctions.call(
            functionName = "getFriendsSnapshot",
            uid = userId,
        ).asMap("Invalid friends response")
        return (data["friends"] as? List<*>)
            .orEmpty()
            .mapNotNull { rawFriend ->
                val friend = rawFriend as? Map<*, *> ?: return@mapNotNull null
                val profile = (friend["profile"] as? Map<*, *>)
                    ?.toUserProfile() ?: return@mapNotNull null
                if (profile.uid.isBlank()) return@mapNotNull null
                val location = friend["location"] as? Map<*, *>
                FriendLocation(
                    id = profile.uid,
                    name = profile.name,
                    phone = profile.phone,
                    avatarUrl = profile.avatarUrl,
                    avatarKey = profile.avatarKey,
                    latitude = location.number("latitude"),
                    longitude = location.number("longitude"),
                    updatedAt = location.number("updatedAt").toLong(),
                    hasOnline = profile.hasOnline,
                    trackingAvailable = profile.trackingAvailable,
                )
            }
    }

    private fun Map<*, *>.toUserProfile(): UserProfile = UserProfile(
        uid = string("uid"),
        name = string("name"),
        phone = string("phone"),
        avatarUrl = string("avatarUrl"),
        avatarKey = string("avatarKey"),
        friendIds = (get("friendIds") as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String },
        hasOnline = get("hasOnline") == true,
        trackingAvailable = get("trackingAvailable") == true,
        secondaryPhones = (get("secondaryPhones") as? Map<*, *>)
            .orEmpty()
            .mapNotNull { (key, value) ->
                val keyString = key as? String ?: return@mapNotNull null
                val valueString = value as? String ?: return@mapNotNull null
                keyString to valueString
            }.toMap(),
    )

    private fun Any?.asMap(message: String): Map<*, *> =
        this as? Map<*, *> ?: error(message)

    private fun Map<*, *>?.number(key: String): Double =
        (this?.get(key) as? Number)?.toDouble() ?: 0.0

    private fun Map<*, *>.string(key: String): String =
        get(key)?.toString().orEmpty()

    private fun mapFriendMutationError(error: Exception): Exception {
        val functionsError = error as? FirebaseFunctionsException ?: return error
        val detailCode = (functionsError.details as? Map<*, *>)?.get("code")
        return if (
            functionsError.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
            detailCode == "FRIEND_LIMIT_REACHED"
        ) {
            FriendLimitReachedException(MAX_FRIEND_COUNT)
        } else {
            error
        }
    }

    private companion object {
        const val TAG = "FriendRepo"
        const val MAX_FRIEND_COUNT = 5
        const val FRIEND_REFRESH_INTERVAL_MS = 10_000L
        const val FRIEND_RETRY_INTERVAL_MS = 8_000L
    }
}
