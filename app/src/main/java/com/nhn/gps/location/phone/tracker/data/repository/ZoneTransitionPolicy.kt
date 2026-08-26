package com.nhn.gps.location.phone.tracker.data.repository

import kotlin.math.max
import kotlin.math.min

internal data class ZoneTransitionDecision(
    val accepted: Boolean,
    val inside: Boolean?,
    val stateChanged: Boolean,
)

/** Pure transition policy shared by zone processing and unit tests. */
internal object ZoneTransitionPolicy {
    private const val MAX_ACCEPTABLE_ACCURACY_METERS = 100f
    private const val MIN_HYSTERESIS_METERS = 5.0
    private const val MAX_HYSTERESIS_METERS = 25.0

    fun evaluate(
        previousInside: Boolean?,
        distanceMeters: Double,
        radiusMeters: Double,
        accuracyMeters: Float?,
    ): ZoneTransitionDecision {
        if (!distanceMeters.isFinite() || !radiusMeters.isFinite() || radiusMeters <= 0.0) {
            return ZoneTransitionDecision(false, previousInside, false)
        }
        if (accuracyMeters != null &&
            (!accuracyMeters.isFinite() || accuracyMeters <= 0f || accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS)
        ) {
            return ZoneTransitionDecision(false, previousInside, false)
        }

        if (previousInside == null) {
            return ZoneTransitionDecision(
                accepted = true,
                inside = distanceMeters <= radiusMeters,
                stateChanged = true,
            )
        }

        val accuracyMargin = accuracyMeters?.toDouble() ?: MIN_HYSTERESIS_METERS
        val maximumForRadius = max(1.0, radiusMeters * 0.25)
        val margin = min(
            min(MAX_HYSTERESIS_METERS, max(MIN_HYSTERESIS_METERS, accuracyMargin)),
            maximumForRadius,
        )
        val nextInside = when {
            previousInside && distanceMeters >= radiusMeters + margin -> false
            !previousInside && distanceMeters <= radiusMeters - margin -> true
            else -> previousInside
        }
        return ZoneTransitionDecision(
            accepted = true,
            inside = nextInside,
            stateChanged = nextInside != previousInside,
        )
    }
}
