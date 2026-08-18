package com.nhn.gps.location.phone.tracker.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

data class DrivingRoute(
    val points: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Long,
)

@Singleton
class GoogleRoutesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun computeDrivingRoute(origin: LatLng, destination: LatLng): DrivingRoute =
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
                    writer.write(createRequestBody(origin, destination).toString())
                }

                val responseCode = connection.responseCode
                val responseBody = (if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (responseCode !in 200..299) {
                    throw IOException(readErrorMessage(responseBody, responseCode))
                }

                parseRoute(responseBody)
            } finally {
                connection.disconnect()
            }
        }

    private fun createRequestBody(origin: LatLng, destination: LatLng): JSONObject =
        JSONObject().apply {
            put("origin", waypoint(origin))
            put("destination", waypoint(destination))
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
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

    private fun parseRoute(responseBody: String): DrivingRoute {
        val route = JSONObject(responseBody)
            .optJSONArray("routes")
            ?.optJSONObject(0)
            ?: throw IOException("No driving route was returned")
        val encodedPolyline = route
            .optJSONObject("polyline")
            ?.optString("encodedPolyline")
            .orEmpty()
        if (encodedPolyline.isBlank()) {
            throw IOException("The route response did not contain a polyline")
        }

        val points = PolyUtil.decode(encodedPolyline)
        if (points.size < 2) {
            throw IOException("The route polyline is empty")
        }

        return DrivingRoute(
            points = points,
            distanceMeters = route.optInt("distanceMeters").coerceAtLeast(0),
            durationSeconds = parseDurationSeconds(route.optString("duration")),
        )
    }

    private fun parseDurationSeconds(value: String): Long = value
        .removeSuffix("s")
        .toDoubleOrNull()
        ?.toLong()
        ?.coerceAtLeast(0L)
        ?: 0L

    private fun readErrorMessage(responseBody: String, responseCode: Int): String {
        val message = runCatching {
            JSONObject(responseBody).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return message?.takeIf { it.isNotBlank() }
            ?: "Routes API request failed with HTTP $responseCode"
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha1(): String? = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageInfo.signatures?.firstOrNull()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-1")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02X".format(byte) }
    }.getOrNull()

    private companion object {
        const val COMPUTE_ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val RESPONSE_FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
