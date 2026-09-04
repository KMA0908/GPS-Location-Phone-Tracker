package com.nhn.gps.location.phone.tracker.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.BuildConfig
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlertType
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.notification.ZoneNotificationManager
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepositoryImpl @Inject constructor(
    private val preferences: AppPreferences,
    private val notificationManager: Lazy<ZoneNotificationManager>,
) : ZoneRepository {
    private val mutex = Mutex()

    override val zones: Flow<List<Zone>> = preferences.zonesJson.map(::decodeZones)
    override val alerts: Flow<List<ZoneAlert>> = preferences.zoneAlertsJson.map(::decodeAlerts)

    override suspend fun upsert(zone: Zone) = mutex.withLock {
        val current = decodeZones(preferences.zonesJson.first()).toMutableList()
        val index = current.indexOfFirst { it.id == zone.id }
        if (index >= 0) current[index] = zone else current += zone
        preferences.setZonesJson(encodeZones(current))
    }

    override suspend fun delete(zoneId: Long) = mutex.withLock {
        val currentZones = decodeZones(preferences.zonesJson.first())
        val filteredZones = currentZones.filterNot { it.id == zoneId }
        
        if (currentZones.size != filteredZones.size) {
            preferences.setZonesJson(encodeZones(filteredZones))
            
            val states = decodeStates(preferences.zoneStatesJson.first()).toMutableMap()
            val keysToRemove = states.keys.filter { it.endsWith(":$zoneId") }
            if (keysToRemove.isNotEmpty()) {
                keysToRemove.forEach { states.remove(it) }
                preferences.setZoneStatesJson(encodeStates(states))
            }
        }
    }

    override suspend fun clearAlerts() = mutex.withLock {
        decodeAlerts(preferences.zoneAlertsJson.first()).forEach { alert ->
            notificationManager.get().cancel(alert.id)
        }
        preferences.setZoneAlertsJson("[]")
    }

    override suspend fun deleteAlert(alertId: Long) = mutex.withLock {
        val current = decodeAlerts(preferences.zoneAlertsJson.first())
        val updated = current.filterNot { it.id == alertId }
        if (updated.size != current.size) {
            preferences.setZoneAlertsJson(encodeAlerts(updated))
            notificationManager.get().cancel(alertId)
        }
    }

    override suspend fun processLocation(
        location: LatLng,
        subjectId: String,
        subjectName: String,
        accuracyMeters: Float?,
    ) = mutex.withLock {
        val zones = decodeZones(preferences.zonesJson.first())
        if (zones.isEmpty()) return@withLock

        val notificationsEnabled = preferences.isNotificationEnabled.first()
        val states = decodeStates(preferences.zoneStatesJson.first()).toMutableMap()
        val alerts = decodeAlerts(preferences.zoneAlertsJson.first()).toMutableList()
        var changed = false
        var alertsChanged = false

        zones.forEach { zone ->
            val distance = SphericalUtil.computeDistanceBetween(location, LatLng(zone.latitude, zone.longitude))
            val key = "$subjectId:${zone.id}"
            val previous = states[key]
            val decision = ZoneTransitionPolicy.evaluate(
                previousInside = previous,
                distanceMeters = distance,
                radiusMeters = zone.radiusMeters.toDouble(),
                accuracyMeters = accuracyMeters,
            )

            if (!decision.accepted) {
                if (BuildConfig.DEBUG && subjectId == "self") {
                    Log.d(TAG, "zone_exit_ignored_low_accuracy zoneId=${zone.id}")
                }
                return@forEach
            }

            val inside = decision.inside ?: return@forEach
            val nearKey = "near:$key"
            val previousNear = states[nearKey]
            val nearDangerous = ZoneNearPolicy.isNearDangerousZone(
                isDangerous = zone.status == ZoneStatus.DANGEROUS,
                isInside = inside,
                distanceMeters = distance,
                radiusMeters = zone.radiusMeters.toDouble(),
            )

            if (previousNear == null || previousNear != nearDangerous) {
                states[nearKey] = nearDangerous
                changed = true
            }

            // A near alert represents approaching a dangerous zone from outside.
            // Initial detection and leaving a dangerous zone only initialize the state.
            if (ZoneNearPolicy.shouldAlert(previous, decision.stateChanged, previousNear, nearDangerous)) {
                val alert = ZoneAlert(
                    id = System.currentTimeMillis(),
                    zoneId = zone.id,
                    zoneName = zone.name,
                    isEnter = false,
                    status = zone.status,
                    time = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    userName = subjectName,
                    type = ZoneAlertType.NEAR_DANGEROUS,
                    userId = subjectId.toAlertUserId(),
                )
                alerts.add(0, alert)
                alertsChanged = true
                if (notificationsEnabled) notificationManager.get().notify(alert)
            }

            if (decision.stateChanged) {
                states[key] = inside
                changed = true

                if (BuildConfig.DEBUG) {
                    val from = previous?.let { if (it) "INSIDE" else "OUTSIDE" } ?: "UNKNOWN"
                    val to = if (inside) "INSIDE" else "OUTSIDE"
                    Log.d(TAG, "zone_transition zoneId=${zone.id} from=$from to=$to")
                }

                // Only alert if we had a previous known state (avoid alert on first detection)
                if (previous != null) {
                    val isEnter = inside
                    if ((isEnter && zone.onEnter) || (!isEnter && zone.onLeave)) {
                        val alert = ZoneAlert(
                            id = System.currentTimeMillis(),
                            zoneId = zone.id,
                            zoneName = zone.name,
                            isEnter = isEnter,
                            status = zone.status,
                            time = System.currentTimeMillis(),
                            latitude = location.latitude,
                            longitude = location.longitude,
                            userName = subjectName,
                            type = when {
                                isEnter -> ZoneAlertType.ENTER
                                zone.status == ZoneStatus.DANGEROUS -> ZoneAlertType.RETURNED_SAFE
                                else -> ZoneAlertType.LEAVE
                            },
                            userId = subjectId.toAlertUserId(),
                        )
                        alerts.add(0, alert)
                        alertsChanged = true
                        if (notificationsEnabled) notificationManager.get().notify(alert)
                        if (BuildConfig.DEBUG && !isEnter) {
                            Log.d(TAG, "zone_exit_notification_sent zoneId=${zone.id}")
                        }
                    }
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "zone_state_initialized zoneId=${zone.id} state=${if (inside) "INSIDE" else "OUTSIDE"}")
                }
            }
        }

        if (changed) {
            preferences.setZoneStatesJson(encodeStates(states))
        }
        if (alertsChanged) {
            preferences.setZoneAlertsJson(encodeAlerts(alerts.take(MAX_ALERTS)))
        }
    }

    private fun encodeZones(items: List<Zone>) = JSONArray().apply {
        items.forEach { zone ->
            put(JSONObject().apply {
                put("id", zone.id)
                put("name", zone.name)
                put("address", zone.address)
                put("latitude", zone.latitude)
                put("longitude", zone.longitude)
                put("type", zone.type.code)
                put("radius", zone.radiusMeters)
                put("onEnter", zone.onEnter)
                put("onLeave", zone.onLeave)
                put("status", zone.status.code)
                put("createdAt", zone.createdAt)
            })
        }
    }.toString()

    private fun decodeZones(raw: String): List<Zone> = runCatching {
        if (raw.isBlank() || raw == "[]") return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Zone(
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    address = item.optString("address"),
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude"),
                    type = ZoneType.fromCode(item.optInt("type")),
                    radiusMeters = item.optInt("radius", 100),
                    onEnter = item.optBoolean("onEnter", true),
                    onLeave = item.optBoolean("onLeave", true),
                    status = ZoneStatus.fromCode(item.optInt("status")),
                    createdAt = item.optLong("createdAt")
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeAlerts(items: List<ZoneAlert>) = JSONArray().apply {
        items.forEach { alert ->
            put(JSONObject().apply {
                put("id", alert.id)
                put("zoneId", alert.zoneId)
                put("zoneName", alert.zoneName)
                put("isEnter", alert.isEnter)
                put("status", alert.status.code)
                put("time", alert.time)
                put("latitude", alert.latitude)
                put("longitude", alert.longitude)
                put("userName", alert.userName)
                put("eventType", alert.type.name)
                put("userId", alert.userId)
            })
        }
    }.toString()

    private fun decodeAlerts(raw: String): List<ZoneAlert> = runCatching {
        if (raw.isBlank() || raw == "[]") return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ZoneAlert(
                    id = item.optLong("id"),
                    zoneId = item.optLong("zoneId"),
                    zoneName = item.optString("zoneName"),
                    isEnter = item.optBoolean("isEnter"),
                    status = ZoneStatus.fromCode(item.optInt("status")),
                    time = item.optLong("time"),
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude"),
                    userName = item.optString("userName", "You"),
                    type = runCatching {
                        ZoneAlertType.valueOf(item.optString("eventType"))
                    }.getOrElse {
                        when {
                            item.optBoolean("isEnter") -> ZoneAlertType.ENTER
                            ZoneStatus.fromCode(item.optInt("status")) == ZoneStatus.DANGEROUS -> ZoneAlertType.RETURNED_SAFE
                            else -> ZoneAlertType.LEAVE
                        }
                    },
                    userId = item.optString("userId"),
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeStates(states: Map<String, Boolean>) = JSONObject(states).toString()
    
    private fun decodeStates(raw: String): Map<String, Boolean> = runCatching {
        if (raw.isBlank() || raw == "{}") return emptyMap()
        val objectJson = JSONObject(raw)
        buildMap {
            objectJson.keys().forEach { key ->
                put(key, objectJson.optBoolean(key))
            }
        }
    }.getOrDefault(emptyMap())

    private fun String.toAlertUserId(): String = removePrefix("friend:")
        .takeUnless { it == "self" }
        .orEmpty()

    private companion object {
        const val TAG = "ZoneRepository"
        const val MAX_ALERTS = 100
    }
}
