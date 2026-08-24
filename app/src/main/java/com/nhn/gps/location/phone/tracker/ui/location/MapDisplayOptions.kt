package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.GoogleMap

enum class MapBaseType(
    val persistedValue: Int,
    val googleMapType: Int,
) {
    NORMAL(
        persistedValue = 1,
        googleMapType = GoogleMap.MAP_TYPE_NORMAL,
    ),
    HYBRID(
        persistedValue = 2,
        googleMapType = GoogleMap.MAP_TYPE_HYBRID,
    ),
    SATELLITE(
        persistedValue = 3,
        googleMapType = GoogleMap.MAP_TYPE_SATELLITE,
    );

    companion object {
        fun fromPersistedValue(value: Int): MapBaseType =
            entries.firstOrNull { it.persistedValue == value } ?: NORMAL
    }
}

data class MapDisplayOptions(
    val baseType: MapBaseType = MapBaseType.NORMAL,
    val trafficEnabled: Boolean = false,
    val buildings3dEnabled: Boolean = false,
) {
    fun selectBaseType(type: MapBaseType): MapDisplayOptions = copy(
        baseType = type,
        buildings3dEnabled = buildings3dEnabled && type == MapBaseType.NORMAL,
    )

    fun toggleTraffic(): MapDisplayOptions {
        val enabled = !trafficEnabled
        return copy(
            trafficEnabled = enabled,
            buildings3dEnabled = if (enabled) false else buildings3dEnabled,
        )
    }

    fun toggleBuildings3d(): MapDisplayOptions {
        val enabled = !buildings3dEnabled
        return copy(
            baseType = if (enabled) MapBaseType.NORMAL else baseType,
            trafficEnabled = if (enabled) false else trafficEnabled,
            buildings3dEnabled = enabled,
        )
    }
}
