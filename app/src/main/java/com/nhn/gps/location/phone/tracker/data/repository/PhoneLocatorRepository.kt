package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface PhoneLocatorRepository {
    suspend fun findUserByPhone(phone: String): Result<Pair<UserProfile, UserLocation?>>
    suspend fun getAddressFromLocation(lat: Double, lng: Double): String?
}

@Singleton
class PhoneLocatorRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : PhoneLocatorRepository {

    private val usersRef = database.getReference("users")

    override suspend fun findUserByPhone(phone: String): Result<Pair<UserProfile, UserLocation?>> = withContext(Dispatchers.IO) {
        try {
            // 1. Check internet connection
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("No internet connection. Please check your network."))
            }

            val query = usersRef.orderByChild("profile/phone").equalTo(phone).limitToFirst(1)
            val snapshot = query.get().await()

            if (snapshot.exists() && snapshot.childrenCount > 0) {
                val userSnapshot = snapshot.children.first()
                
                // 2. Validate UserProfile exists
                val profile = userSnapshot.child("profile").getValue(UserProfile::class.java)
                    ?: return@withContext Result.failure(Exception("User found, but profile information is missing."))
                
                // 3. Get UserLocation and validate coordinates
                val location = userSnapshot.child("location").getValue(UserLocation::class.java)
                
                if (location != null) {
                    if (!isValidCoordinate(location.latitude, location.longitude)) {
                        return@withContext Result.failure(Exception("Found user, but their location coordinates are invalid."))
                    }
                } else {
                    // We allow success without location, UI will handle the toast
                }

                Result.success(Pair(profile, location))
            } else {
                Result.failure(Exception("No user found with this phone number."))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(Exception("Firebase Error: ${e.localizedMessage ?: "Unknown database error."}"))
        }
    }

    override suspend fun getAddressFromLocation(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!isValidCoordinate(lat, lng)) return@withContext null
        
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val addresses = kotlinx.coroutines.suspendCancellableCoroutine<List<android.location.Address>?> { continuation ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        continuation.resume(addresses)
                    }
                }
                formatAddress(addresses?.firstOrNull())
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                formatAddress(addresses?.firstOrNull())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && lat != 0.0 && lng != 0.0
    }

    private fun formatAddress(address: android.location.Address?): String? {
        if (address == null) return null
        val city = address.locality ?: address.subAdminArea ?: ""
        val country = address.countryName ?: ""
        return when {
            city.isNotBlank() && country.isNotBlank() -> "$city, $country"
            else -> city.ifBlank { country }.ifBlank { null }
        }
    }
}
