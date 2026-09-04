package com.nhn.gps.location.phone.tracker.data.repository

import android.net.Uri
import com.nhn.gps.location.phone.tracker.data.local.InstallationIdentity
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.util.AvatarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getUserProfile(uid: String): UserProfile?
    suspend fun getOrCreateInstallationId(): String
    suspend fun createReplacementInstallationId(): String
    suspend fun saveUserProfile(uid: String, profile: UserProfile)
    suspend fun updateAvailability(uid: String, hasOnline: Boolean)
    suspend fun setLocationSharing(uid: String, enabled: Boolean)
    suspend fun uploadAvatar(uid: String, imageUri: Uri): String
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val installationIdentity: InstallationIdentity,
    private val ownerFunctions: OwnerFunctionClient,
) : UserRepository {

    override suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        if (!FirebasePathKey.isValid(uid)) return@withContext null
        val data = ownerFunctions.call("getOwnProfile", uid) as? Map<*, *>
            ?: return@withContext null
        val profile = data["profile"] as? Map<*, *> ?: return@withContext null
        UserProfile(
            uid = profile.string("uid"),
            name = profile.string("name"),
            phone = profile.string("phone"),
            avatarUrl = profile.string("avatarUrl"),
            avatarKey = profile.string("avatarKey"),
            friendIds = (profile["friendIds"] as? List<*>)
                .orEmpty()
                .mapNotNull { it as? String },
            hasOnline = profile["hasOnline"] == true,
            trackingAvailable = profile["trackingAvailable"] == true,
            secondaryPhones = (profile["secondaryPhones"] as? Map<*, *>)
                .orEmpty()
                .mapNotNull { (key, value) ->
                    val keyString = key as? String ?: return@mapNotNull null
                    val valueString = value as? String ?: return@mapNotNull null
                    keyString to valueString
                }.toMap(),
        )
    }

    override suspend fun getOrCreateInstallationId(): String = withContext(Dispatchers.IO) {
        installationIdentity.getOrCreateId()
    }

    override suspend fun createReplacementInstallationId(): String = withContext(Dispatchers.IO) {
        installationIdentity.rotateForNewProfile()
    }

    override suspend fun saveUserProfile(uid: String, profile: UserProfile): Unit = withContext(Dispatchers.IO) {
        FirebasePathKey.requireValid(uid, "Installation ID")
        val name = profile.name.trim()
        val phone = profile.phone.trim()
        require(name.isNotBlank()) { "Profile name is required" }
        require(name.length <= MAX_PROFILE_NAME_LENGTH) { "Profile name is too long" }
        require(phone.length <= MAX_PHONE_LENGTH) { "Phone number is too long" }
        ownerFunctions.call(
            functionName = "upsertProfile",
            uid = uid,
            values = mapOf(
                "profile" to mapOf(
                    "name" to name,
                    "phone" to phone,
                    "avt" to profile.avatarUrl.trim(),
                    "avatarKey" to AvatarHelper.normalizeKey(profile.avatarKey),
                    "secondaryPhones" to profile.secondaryPhones,
                ),
            ),
        )
    }

    override suspend fun updateAvailability(uid: String, hasOnline: Boolean): Unit =
        withContext(Dispatchers.IO) {
        FirebasePathKey.requireValid(uid, "Installation ID")
        ownerFunctions.call(
            functionName = "updateAvailability",
            uid = uid,
            values = mapOf("hasOnline" to hasOnline),
        )
    }

    override suspend fun setLocationSharing(uid: String, enabled: Boolean): Unit =
        withContext(Dispatchers.IO) {
            FirebasePathKey.requireValid(uid, "Installation ID")
            ownerFunctions.call(
                functionName = "setLocationSharing",
                uid = uid,
                values = mapOf("enabled" to enabled),
            )
        }

    override suspend fun uploadAvatar(uid: String, imageUri: Uri): String = withContext(Dispatchers.IO) {
        FirebasePathKey.requireValid(uid, "Installation ID")
        @Suppress("UNUSED_VARIABLE") val ignored = imageUri
        throw UnsupportedOperationException(
            "Custom avatar upload is disabled until an owner-verified upload endpoint is available",
        )
    }

    private companion object {
        const val MAX_PROFILE_NAME_LENGTH = 100
        const val MAX_PHONE_LENGTH = 32
    }

    private fun Map<*, *>.string(key: String): String =
        get(key)?.toString().orEmpty()

}
