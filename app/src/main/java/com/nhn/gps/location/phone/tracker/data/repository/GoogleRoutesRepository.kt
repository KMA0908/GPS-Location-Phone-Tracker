package com.nhn.gps.location.phone.tracker.data.repository

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.nhn.gps.location.phone.tracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class GoogleRouteTravelMode(val apiValue: String, val supportsTrafficAware: Boolean) {
    DRIVE("DRIVE", true),
    TWO_WHEELER("TWO_WHEELER", true),
    WALK("WALK", false),
}

data class GoogleRoute(
    val points: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Long,
)

class GoogleRoutesException(
    val httpCode: Int,
    val backendStatus: String?,
    val backendMessage: String,
) : IOException(backendMessage)

class GoogleRoutesNoRouteException : IOException("No route was returned")

class GoogleRoutesInvalidResponseException(message: String) : IOException(message)

@Singleton
class GoogleRoutesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun hasSigningCertificate(): Boolean = signingCertificateSha1() != null

    suspend fun computeRoute(
        origin: LatLng,
        destination: LatLng,
        travelMode: GoogleRouteTravelMode,
    ): GoogleRoute =
        withContext(Dispatchers.IO) {
            val connection = (URL(COMPUTE_ROUTES_URL).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty(
                    "X-Goog-Api-Key",
                    context.getString(R.string.routes_api_key),
                )
                connection.setRequestProperty("X-Goog-FieldMask", RESPONSE_FIELD_MASK)
                connection.setRequestProperty("X-Android-Package", context.packageName)
                signingCertificateSha1()?.let {
                    connection.setRequestProperty("X-Android-Cert", it)
                }

                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(GoogleRoutesCodec.createRequestBody(origin, destination, travelMode).toString())
                }

                val responseCode = connection.responseCode
                val responseBody = (if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (responseCode !in 200..299) {
                    val backendError = readBackendError(responseBody, responseCode)
                    throw GoogleRoutesException(
                        httpCode = responseCode,
                        backendStatus = backendError.status,
                        backendMessage = backendError.message,
                    )
                }

                GoogleRoutesCodec.parseRoute(responseBody)
            } finally {
                connection.disconnect()
            }
        }

    private fun readBackendError(responseBody: String, responseCode: Int): BackendError {
        val errorObject = runCatching {
            JSONObject(responseBody).optJSONObject("error")
        }.getOrNull()
        return BackendError(
            status = errorObject?.optString("status")?.takeIf { it.isNotBlank() },
            message = errorObject?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Routes API request failed with HTTP $responseCode",
        )
    }

    private fun signingCertificateSha1(): String? = runCatching {
        val signature = PackageInfoCompat.getSignatures(
            context.packageManager,
            context.packageName,
        ).firstOrNull() ?: return@runCatching null
        MessageDigest.getInstance("SHA-1")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02X".format(byte) }
    }.getOrNull()

    private data class BackendError(
        val status: String?,
        val message: String,
    )

    private companion object {
        const val COMPUTE_ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val RESPONSE_FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}

internal object GoogleRoutesCodec {
    fun createRequestBody(
        origin: LatLng,
        destination: LatLng,
        travelMode: GoogleRouteTravelMode,
    ): JSONObject =
        JSONObject().apply {
            put("origin", waypoint(origin))
            put("destination", waypoint(destination))
            put("travelMode", travelMode.apiValue)
            if (travelMode.supportsTrafficAware) {
                put("routingPreference", "TRAFFIC_AWARE")
            }
            put("polylineQuality", "HIGH_QUALITY")
            put("computeAlternativeRoutes", false)
            put("languageCode", Locale.getDefault().toLanguageTag())
            put("units", "METRIC")
        }

    private fun waypoint(point: LatLng): JSONObject = JSONObject().apply {
        put(
            "location",
            JSONObject().put(
                "latLng",
                JSONObject()
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude),
            ),
        )
    }

    fun parseRoute(responseBody: String): GoogleRoute {
        val route = JSONObject(responseBody)
            .optJSONArray("routes")
            ?.optJSONObject(0)
            ?: throw GoogleRoutesNoRouteException()
        val encodedPolyline = route
            .optJSONObject("polyline")
            ?.optString("encodedPolyline")
            .orEmpty()
        if (encodedPolyline.isBlank()) {
            throw GoogleRoutesInvalidResponseException(
                "The route response did not contain a polyline",
            )
        }

        val points = PolyUtil.decode(encodedPolyline)
        if (points.size < 2) {
            throw GoogleRoutesInvalidResponseException("The route polyline is empty")
        }

        return GoogleRoute(
            points = points,
            distanceMeters = route.optInt("distanceMeters").coerceAtLeast(0),
            durationSeconds = parseDurationSeconds(route.optString("duration")),
        )
    }

    fun parseDurationSeconds(value: String): Long = value
        .removeSuffix("s")
        .toDoubleOrNull()
        ?.toLong()
        ?.coerceAtLeast(0L)
        ?: 0L

}
