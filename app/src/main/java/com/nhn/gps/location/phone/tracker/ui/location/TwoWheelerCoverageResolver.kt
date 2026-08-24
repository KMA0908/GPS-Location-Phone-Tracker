package com.nhn.gps.location.phone.tracker.ui.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class TwoWheelerCoverageResult(
    val originCountryCode: String?,
    val destinationCountryCode: String?,
    val isSupported: Boolean?,
)

object TwoWheelerCountryCoverage {
    // Google Routes API two-wheeled vehicle coverage, checked August 2026.
    // https://developers.google.com/maps/documentation/routes/coverage-two-wheeled
    private val supportedCountryCodes = setOf(
        "AR", "BD", "BJ", "BO", "BR", "CL", "CO", "CR", "DZ", "EC",
        "EG", "GH", "GT", "HK", "HN", "ID", "IN", "KE", "KH", "LA",
        "LK", "MM", "MX", "MY", "NG", "NI", "PE", "PH", "PK", "PY",
        "RW", "SG", "TG", "TH", "TN", "TW", "UG", "UY", "VN", "ZA",
    )

    fun isSupportedCountry(countryCode: String): Boolean =
        countryCode.trim().uppercase(Locale.US) in supportedCountryCodes
}

@Singleton
class TwoWheelerCoverageResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun resolve(origin: LatLng, destination: LatLng): TwoWheelerCoverageResult {
        val originCountryCode = resolveCountryCode(origin)
        val destinationCountryCode = resolveCountryCode(destination)
        val knownCountryCodes = listOfNotNull(originCountryCode, destinationCountryCode)
            .distinct()
        val isSupported = knownCountryCodes
            .takeIf(List<String>::isNotEmpty)
            ?.all(TwoWheelerCountryCoverage::isSupportedCountry)

        return TwoWheelerCoverageResult(
            originCountryCode = originCountryCode,
            destinationCountryCode = destinationCountryCode,
            isSupported = isSupported,
        )
    }

    private suspend fun resolveCountryCode(point: LatLng): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<Address>?> { continuation ->
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1)
            }
            addresses?.firstOrNull()?.countryCode
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.uppercase(Locale.US)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
