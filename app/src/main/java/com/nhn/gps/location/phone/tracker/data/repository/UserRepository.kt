package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun findUserByPhone(phone: String): UserProfile?
    suspend fun signInAnonymously(): String
    suspend fun saveUserProfile(uid: String, profile: UserProfile)
    fun getCurrentUserId(): String?
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
) : UserRepository {

    private val usersRef = database.getReference("users")

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override suspend fun findUserByPhone(phone: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val query = usersRef.orderByChild("profile/phone").equalTo(phone).limitToFirst(1)
            val snapshot = query.get().await()

            if (snapshot.exists() && snapshot.childrenCount > 0) {
                val userSnapshot = snapshot.children.first()
                val profile = userSnapshot.child("profile").getValue(UserProfile::class.java)
                // Đảm bảo UID trong object profile khớp với node key trong database
                profile?.copy(uid = userSnapshot.key ?: profile.uid)
            } else {
                null
            }
        } catch (e: Exception) {
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
            val errorMsg = e.localizedMessage ?: "Unknown error"
            if (errorMsg.contains("CONFIGURATION_NOT_FOUND", true)) {
                throw Exception("Firebase Auth Error: Please check if 'Anonymous' provider is ENABLED in Firebase Console AND your Package Name matches in google-services.json")
            }
            throw Exception("Sign-in failed: $errorMsg")
        }
    }

    override suspend fun saveUserProfile(uid: String, profile: UserProfile): Unit = withContext(Dispatchers.IO) {
        try {
            // Đảm bảo profile.uid luôn trùng với Auth UID (uid truyền vào)
            val profileToSave = profile.copy(uid = uid)
            usersRef.child(uid).child("profile").setValue(profileToSave).await()
        } catch (e: Exception) {
            throw Exception("Database Write Error: ${e.localizedMessage}. Check your Firebase Rules and Internet connection.")
        }
    }
}
