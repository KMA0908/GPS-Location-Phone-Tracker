package com.nhn.gps.location.phone.tracker.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.maps.android.PolyUtil
import com.nhn.gps.location.phone.tracker.BuildConfig
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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
    private val appCheck: FirebaseAppCheck,
) {

    suspend fun computeRoute(
        origin: LatLng,
        destination: LatLng,
        travelMode: GoogleRouteTravelMode,
    ): GoogleRoute = withContext(Dispatchers.IO) {
        val appCheckToken = appCheckToken()
        val requestBody = JSONObject().put(
            "data",
            JSONObject()
                .put("origin", coordinate(origin))
                .put("destination", coordinate(destination))
                .put("travelMode", travelMode.apiValue),
        )
        val connection = (URL(BuildConfig.ROUTES_FUNCTION_URL).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseJson = runCatching { JSONObject(responseBody) }.getOrNull()
            if (responseCode !in 200..299) {
                val error = responseJson?.optJSONObject("error")
                throw GoogleRoutesException(
                    httpCode = responseCode,
                    backendStatus = error?.optString("status")?.takeIf(String::isNotBlank),
                    backendMessage = error?.optString("message")?.takeIf(String::isNotBlank)
                        ?: "Route function failed with HTTP $responseCode",
                )
            }

            val result = responseJson?.optJSONObject("result")
                ?: throw GoogleRoutesInvalidResponseException(
                    "The route function returned an invalid response",
                )
            val route = GoogleRoutesCodec.parseRoute(result)
            Log.d("RoutesRepo", "route_request_success mode=${travelMode.apiValue} distanceMeters=${route.distanceMeters} durationSeconds=${route.durationSeconds}")
            route
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun appCheckToken(): String {
        return appCheck.getAppCheckToken(false).await().token.takeIf(String::isNotBlank)
            ?: throw GoogleRoutesException(
                httpCode = 403,
                backendStatus = "APP_CHECK_FAILED",
                backendMessage = "App Check token is unavailable",
            )
    }

    private fun coordinate(point: LatLng): JSONObject = JSONObject()
        .put("latitude", point.latitude)
        .put("longitude", point.longitude)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

internal object GoogleRoutesCodec {
    fun parseRoute(response: JSONObject): GoogleRoute {
        val encodedPolyline = response.optString("encodedPolyline")
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
            distanceMeters = response.optInt("distanceMeters").coerceAtLeast(0),
            durationSeconds = response.optLong("durationSeconds").coerceAtLeast(0L),
        )
    }
}
