package com.nhn.gps.location.phone.tracker.data.repository

import android.net.Uri
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.util.AvatarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun findUserByPhone(phone: String): UserProfile?
    suspend fun getUserProfile(uid: String): UserProfile?
    suspend fun signInAnonymously(): String
    suspend fun saveUserProfile(uid: String, profile: UserProfile)
    suspend fun uploadAvatar(uid: String, imageUri: Uri): String
    fun getCurrentUserId(): String?
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) : UserRepository {

    private val usersRef = database.getReference("users")

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        usersRef.child(uid).child("profile").get().await()
            .getValue(UserProfile::class.java)
            ?.copy(uid = uid)
    }

    override suspend fun findUserByPhone(phone: String): UserProfile? = withContext(Dispatchers.IO) {
        val query = usersRef.orderByChild("profile/phone").equalTo(phone).limitToFirst(1)
        val snapshot = query.get().await()

        if (snapshot.exists() && snapshot.childrenCount > 0) {
            val userSnapshot = snapshot.children.first()
            val profile = userSnapshot.child("profile").getValue(UserProfile::class.java)
            profile?.copy(uid = userSnapshot.key ?: profile.uid)
        } else {
            null
        }
    }

    override suspend fun signInAnonymously(): String = withContext(Dispatchers.IO) {
        try {
            val currentUid = auth.currentUser?.uid
            if (currentUid != null) return@withContext currentUid
            
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: throw Exception("Auth succeeded but UID is null")
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Anonymous sign-in failed", e)
            val errorMsg = e.localizedMessage ?: "Unknown error"
            
            val friendlyMessage = when {
                errorMsg.contains("CONFIGURATION_NOT_FOUND", true) -> 
                    "Firebase Auth Error: Please check if 'Anonymous' provider is ENABLED in Firebase Console AND your Package Name matches in google-services.json"
                errorMsg.contains("app-not-authorized", true) || errorMsg.contains("Play Integrity", true) ->
                    "Unable to verify this device. Please update Google Play services and try again."
                else -> "Sign-in failed: $errorMsg"
            }
            throw Exception(friendlyMessage, e)
        }
    }

    override suspend fun saveUserProfile(uid: String, profile: UserProfile): Unit = withContext(Dispatchers.IO) {
        try {
            val profileToSave = profile.copy(uid = uid)
            val firebaseProfile = mapOf(
                "uid" to profileToSave.uid,
                "name" to profileToSave.name,
                "phone" to profileToSave.phone,
                // Local drawable resource IDs/URIs are not portable between devices.
                // Store the stable key so every installation resolves the same bundled image.
                "avatarKey" to AvatarHelper.normalizeKey(profileToSave.avatarKey),
            )
            usersRef.child(uid).child("profile").setValue(firebaseProfile).await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw e
        }
    }

    override suspend fun uploadAvatar(uid: String, imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val storageRef = storage.reference.child("avatars/$uid/profile.jpg")
            storageRef.putFile(imageUri).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            throw Exception("Avatar Upload Error: ${e.localizedMessage}. Check your Firebase Storage Rules.")
        }
    }
}
