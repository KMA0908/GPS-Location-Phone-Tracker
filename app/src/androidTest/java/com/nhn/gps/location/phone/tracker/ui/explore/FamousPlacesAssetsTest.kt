package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamousPlacesAssetsTest {

    @Test
    fun famousPlacesJsonAndReferencedImagesAreValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json = context.assets.open("famous_places.json").bufferedReader().use { it.readText() }
        val places = JSONArray(json)
        val ids = mutableSetOf<Int>()

        assertEquals(280, places.length())

        for (index in 0 until places.length()) {
            val place = places.getJSONObject(index)
            assertTrue(ids.add(place.getInt("id")))
            assertTrue(place.getString("placeName").isNotBlank())
            assertTrue(place.getDouble("latitude") in -90.0..90.0)
            assertTrue(place.getDouble("longitude") in -180.0..180.0)

            val photos = place.optJSONArray("previewPhotos") ?: JSONArray()
            for (photoIndex in 0 until photos.length()) {
                val fileName = photos.getString(photoIndex)
                assertTrue(fileName.matches(Regex("[a-z0-9_]+\\.webp")))
                context.assets.open("famous_places_images/$fileName").use { }
            }
        }
    }
}
