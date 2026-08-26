package com.nhn.gps.location.phone.tracker.data.repository

import com.google.android.gms.maps.model.LatLng
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import kotlinx.coroutines.flow.Flow

interface ZoneRepository {
    val zones: Flow<List<Zone>>
    val alerts: Flow<List<ZoneAlert>>

    suspend fun upsert(zone: Zone)
    suspend fun delete(zoneId: Long)
    suspend fun deleteAlert(alertId: Long)
    suspend fun clearAlerts()
    suspend fun processLocation(
        location: LatLng,
        subjectId: String = "self",
        subjectName: String = "You",
        accuracyMeters: Float? = null,
    )
}
