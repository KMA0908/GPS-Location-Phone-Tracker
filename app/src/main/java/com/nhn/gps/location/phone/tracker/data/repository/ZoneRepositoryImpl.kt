package com.nhn.gps.location.phone.tracker.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.notification.ZoneNotificationManager
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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
        preferences.saveZoneData(encodeZones(current), preferences.zoneAlertsJson.first(), preferences.zoneStatesJson.first())
    }

    override suspend fun delete(zoneId: Long) = mutex.withLock {
        val zones = decodeZones(preferences.zonesJson.first()).filterNot { it.id == zoneId }
        val states = decodeStates(preferences.zoneStatesJson.first()).toMutableMap().also { stateMap ->
            stateMap.keys.filter { key -> key.substringAfterLast(':') == zoneId.toString() }.forEach(stateMap::remove)
        }
        preferences.saveZoneData(encodeZones(zones), preferences.zoneAlertsJson.first(), encodeStates(states))
    }

    override suspend fun clearAlerts() = mutex.withLock {
        preferences.saveZoneData(preferences.zonesJson.first(), "[]", preferences.zoneStatesJson.first())
    }

    override suspend fun processLocation(location: LatLng, subjectId: String, subjectName: String) = mutex.withLock {
        val zones = decodeZones(preferences.zonesJson.first())
        if (zones.isEmpty()) return
        val states = decodeStates(preferences.zoneStatesJson.first()).toMutableMap()
        val alerts = decodeAlerts(preferences.zoneAlertsJson.first()).toMutableList()
        var changed = false
        zones.forEach { zone ->
            val inside = SphericalUtil.computeDistanceBetween(
                location,
                LatLng(zone.latitude, zone.longitude)
            ) <= zone.radiusMeters
            val key = "$subjectId:${zone.id}"
            val previous = states[key]
            states[key] = inside
            if (previous == null || previous == inside) return@forEach
            val isEnter = inside
            if ((isEnter && !zone.onEnter) || (!isEnter && !zone.onLeave)) return@forEach
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
            )
            alerts.add(0, alert)
            changed = true
            notificationManager.get().notify(alert)
        }
        if (changed || states.isNotEmpty()) {
            preferences.saveZoneData(encodeZones(zones), encodeAlerts(alerts.take(MAX_ALERTS)), encodeStates(states))
        }
    }

    private fun encodeZones(items: List<Zone>) = JSONArray().apply {
        items.forEach { zone ->
            put(JSONObject().apply {
                put("id", zone.id); put("name", zone.name); put("address", zone.address)
                put("latitude", zone.latitude); put("longitude", zone.longitude)
                put("type", zone.type.code); put("radius", zone.radiusMeters)
                put("onEnter", zone.onEnter); put("onLeave", zone.onLeave)
                put("status", zone.status.code); put("createdAt", zone.createdAt)
            })
        }
    }.toString()

    private fun decodeZones(raw: String): List<Zone> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Zone(
                    id = item.optLong("id"), name = item.optString("name"), address = item.optString("address"),
                    latitude = item.optDouble("latitude"), longitude = item.optDouble("longitude"),
                    type = ZoneType.fromCode(item.optInt("type")), radiusMeters = item.optInt("radius", 100),
                    onEnter = item.optBoolean("onEnter", true), onLeave = item.optBoolean("onLeave", true),
                    status = ZoneStatus.fromCode(item.optInt("status")), createdAt = item.optLong("createdAt")
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeAlerts(items: List<ZoneAlert>) = JSONArray().apply {
        items.forEach { alert ->
            put(JSONObject().apply {
                put("id", alert.id); put("zoneId", alert.zoneId); put("zoneName", alert.zoneName)
                put("isEnter", alert.isEnter); put("status", alert.status.code); put("time", alert.time)
                put("latitude", alert.latitude); put("longitude", alert.longitude); put("userName", alert.userName)
            })
        }
    }.toString()

    private fun decodeAlerts(raw: String): List<ZoneAlert> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ZoneAlert(
                    id = item.optLong("id"), zoneId = item.optLong("zoneId"), zoneName = item.optString("zoneName"),
                    isEnter = item.optBoolean("isEnter"), status = ZoneStatus.fromCode(item.optInt("status")),
                    time = item.optLong("time"), latitude = item.optDouble("latitude"), longitude = item.optDouble("longitude"),
                    userName = item.optString("userName", "You")
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeStates(states: Map<String, Boolean>) = JSONObject(states).toString()
    private fun decodeStates(raw: String): Map<String, Boolean> = runCatching {
        val objectJson = JSONObject(raw)
        buildMap { objectJson.keys().forEach { key -> put(key, objectJson.optBoolean(key)) } }
    }.getOrDefault(emptyMap())

    private companion object { const val MAX_ALERTS = 100 }
}
