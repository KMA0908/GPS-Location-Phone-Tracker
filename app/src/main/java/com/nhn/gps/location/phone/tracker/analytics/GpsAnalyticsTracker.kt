package com.nhn.gps.location.phone.tracker.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.nhn.gps.location.phone.tracker.BuildConfig
import java.util.Locale

/** Firebase event adapter following the same defensive pattern used by AI Voice. */
object GpsAnalyticsTracker {
    private const val TAG = "GpsAnalytics"
    private const val MAX_EVENT_NAME_LENGTH = 40
    private const val MAX_PARAM_STRING_LENGTH = 100

    @Volatile
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val safeEventName = eventName.toAnalyticsEventName()
        val bundle = params.toAnalyticsBundle()
        val analytics = firebaseAnalytics
        if (analytics == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Firebase Analytics is not initialized")
            return
        }

        runCatching { analytics.logEvent(safeEventName, bundle) }
            .onFailure { error ->
                if (BuildConfig.DEBUG) Log.w(TAG, "Firebase log failed: $safeEventName", error)
            }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "event=$safeEventName params=${params.filterValues { it != null }}")
        }
    }

    private fun Map<String, Any?>.toAnalyticsBundle(): Bundle? {
        if (isEmpty()) return null
        return Bundle().also { bundle ->
            forEach { (key, value) ->
                if (value != null) bundle.putAnalyticsValue(key.toAnalyticsParamName(), value)
            }
        }
    }

    private fun Bundle.putAnalyticsValue(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value.take(MAX_PARAM_STRING_LENGTH))
            is Int -> putLong(key, value.toLong())
            is Long -> putLong(key, value)
            is Float -> putDouble(key, value.toDouble())
            is Double -> putDouble(key, value)
            is Boolean -> putString(key, value.toString())
            else -> putString(key, value.toString().take(MAX_PARAM_STRING_LENGTH))
        }
    }

    private fun String.toAnalyticsEventName(): String = toAnalyticsName("event")

    private fun String.toAnalyticsParamName(): String = toAnalyticsName("param")

    private fun String.toAnalyticsName(fallback: String): String {
        val sanitized = toSnakeCase()
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { fallback }
            .let { value ->
                if (value.firstOrNull()?.isLetter() == true) value else "${fallback}_$value"
            }
            .let { value ->
                if (
                    value.startsWith("firebase_") ||
                    value.startsWith("google_") ||
                    value.startsWith("ga_")
                ) {
                    "app_$value"
                } else {
                    value
                }
            }
        return sanitized
            .take(MAX_EVENT_NAME_LENGTH)
            .trimEnd('_')
            .ifBlank { fallback }
    }

    private fun String.toSnakeCase(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9_]+"), "_")
            .trim('_')
            .lowercase(Locale.US)
}
