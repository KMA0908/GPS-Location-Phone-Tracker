package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.functions.FirebaseFunctionsException
import com.nhn.gps.location.phone.tracker.data.local.InstallationIdentity
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface PhoneLocatorRepository {
    suspend fun findUserByPhone(phone: String): Result<List<PhoneLocatorMatch>>
    suspend fun getAddressFromLocation(lat: Double, lng: Double): String?
    suspend fun getLocationFromAddress(query: String): Result<GeocodedLocation?>
}

data class PhoneLocatorMatch(
    val profile: UserProfile,
    val location: UserLocation?,
)

data class GeocodedLocation(
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String,
    val placeId: String? = null,
)

class GeocoderUnavailableException : Exception("Geocoder not available")

@Singleton
class PhoneLocatorRepositoryImpl @Inject constructor(
    private val ownerFunctions: OwnerFunctionClient,
    private val installationIdentity: InstallationIdentity,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : PhoneLocatorRepository {

    override suspend fun findUserByPhone(phone: String): Result<List<PhoneLocatorMatch>> = withContext(Dispatchers.IO) {
        try {
            // 1. Check internet connection
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("No internet connection. Please check your network."))
            }

            val normalizedPhone = phone.trim()
            require(normalizedPhone.length <= MAX_PHONE_LENGTH) { "Phone number is too long" }
            val data = ownerFunctions.call(
                functionName = "findUserByPhone",
                uid = installationIdentity.getOrCreateId(),
                values = mapOf("phone" to normalizedPhone),
            ) as? Map<*, *> ?: error("Invalid phone lookup response")
            val matchMaps = (data["matches"] as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                .orEmpty()
            val compatibleMatches = if (matchMaps.isEmpty() && data["profile"] is Map<*, *>) {
                listOf(data)
            } else {
                matchMaps
            }
            val matches = compatibleMatches.map(::parseMatch)
            require(matches.isNotEmpty()) { "No user found with this phone number." }
            Result.success(matches)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (
                e is FirebaseFunctionsException &&
                e.code == FirebaseFunctionsException.Code.NOT_FOUND
            ) {
                Result.failure(Exception("No user found with this phone number."))
            } else {
                Result.failure(Exception(e.localizedMessage ?: "Phone lookup failed."))
            }
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

    override suspend fun getLocationFromAddress(query: String): Result<GeocodedLocation?> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext Result.failure(Exception("Empty query"))

        if (!android.location.Geocoder.isPresent()) {
            return@withContext Result.failure(GeocoderUnavailableException())
        }

        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            val addresses = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                kotlinx.coroutines.suspendCancellableCoroutine<List<android.location.Address>?> { continuation ->
                    geocoder.getFromLocationName(trimmedQuery, 1) { addresses ->
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(trimmedQuery, 1)
            }

            val address = addresses?.firstOrNull {
                isValidCoordinate(it.latitude, it.longitude)
            }
            if (address != null) {
                val loc = GeocodedLocation(
                    latitude = address.latitude,
                    longitude = address.longitude,
                    formattedAddress = address.getAddressLine(0) ?: trimmedQuery,
                )
                Result.success(loc)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
        return actNw.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun parseMatch(match: Map<*, *>): PhoneLocatorMatch {
        val profileData = match["profile"] as? Map<*, *>
            ?: error("Invalid user profile response")
        val profile = UserProfile(
            uid = profileData.string("uid"),
            name = profileData.string("name"),
            phone = profileData.string("phone"),
            avatarUrl = profileData.string("avatarUrl"),
            avatarKey = profileData.string("avatarKey"),
            hasOnline = profileData["hasOnline"] == true,
            trackingAvailable = profileData["trackingAvailable"] == true,
        )
        require(profile.uid.isNotBlank()) { "Invalid user profile response" }
        val locationData = match["location"] as? Map<*, *>
        val location = locationData?.let {
            UserLocation(
                latitude = it.number("latitude"),
                longitude = it.number("longitude"),
                updatedAt = it.number("updatedAt").toLong(),
            )
        }?.takeIf { isValidCoordinate(it.latitude, it.longitude) }
        return PhoneLocatorMatch(profile, location)
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)
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

    private fun Map<*, *>.string(key: String): String = get(key)?.toString().orEmpty()

    private fun Map<*, *>.number(key: String): Double =
        (get(key) as? Number)?.toDouble() ?: Double.NaN

    private companion object {
        const val MAX_PHONE_LENGTH = 32
    }
}
