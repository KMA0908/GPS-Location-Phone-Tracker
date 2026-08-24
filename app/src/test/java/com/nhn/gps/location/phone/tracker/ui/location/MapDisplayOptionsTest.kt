package com.nhn.gps.location.phone.tracker.ui.location

import com.google.android.gms.maps.GoogleMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDisplayOptionsTest {

    @Test
    fun `base map types match the decompiled app`() {
        assertEquals(
            listOf(MapBaseType.NORMAL, MapBaseType.HYBRID, MapBaseType.SATELLITE),
            MapBaseType.entries,
        )
        assertEquals(GoogleMap.MAP_TYPE_NORMAL, MapBaseType.NORMAL.googleMapType)
        assertEquals(GoogleMap.MAP_TYPE_HYBRID, MapBaseType.HYBRID.googleMapType)
        assertEquals(GoogleMap.MAP_TYPE_SATELLITE, MapBaseType.SATELLITE.googleMapType)
    }

    @Test
    fun `enabling traffic disables 3d buildings`() {
        val result = MapDisplayOptions(
            buildings3dEnabled = true,
        ).toggleTraffic()

        assertTrue(result.trafficEnabled)
        assertFalse(result.buildings3dEnabled)
    }

    @Test
    fun `enabling 3d selects normal and disables traffic`() {
        val result = MapDisplayOptions(
            baseType = MapBaseType.HYBRID,
            trafficEnabled = true,
        ).toggleBuildings3d()

        assertEquals(MapBaseType.NORMAL, result.baseType)
        assertFalse(result.trafficEnabled)
        assertTrue(result.buildings3dEnabled)
    }

    @Test
    fun `selecting satellite disables 3d buildings`() {
        val result = MapDisplayOptions(
            baseType = MapBaseType.NORMAL,
            buildings3dEnabled = true,
        ).selectBaseType(MapBaseType.SATELLITE)

        assertEquals(MapBaseType.SATELLITE, result.baseType)
        assertFalse(result.buildings3dEnabled)
    }
}
