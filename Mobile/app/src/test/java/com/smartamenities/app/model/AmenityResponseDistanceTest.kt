package com.smartamenities.app.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that AmenityResponse distanceMeters is treated as backend-authoritative
 * and is NOT recomputed on Android.
 */
class AmenityResponseDistanceTest {

    private val gson = Gson()

    @Test
    fun `preserves backend distanceMeters exactly without modification`() {
        val json = """
            {
              "id": "men_restroom_1",
              "name": "Men's Restroom",
              "amenityType": "MEN_RESTROOM",
              "level": "Level3",
              "latitude": 32.8965,
              "longitude": -97.0438,
              "distanceMeters": 26.47
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, AmenityResponse::class.java)

        // Exact value from backend—no rounding or modification in Android parsing
        assertEquals(26.47, parsed.distanceMeters!!, 0.0001)
    }

    @Test
    fun `handles null distanceMeters gracefully (route distance unavailable from backend)`() {
        val json = """
            {
              "id": "women_restroom_1",
              "name": "Women's Restroom",
              "amenityType": "WOMEN_RESTROOM",
              "level": "Level3",
              "latitude": 32.8967,
              "longitude": -97.0440,
              "distanceMeters": null
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, AmenityResponse::class.java)

        assertTrue(parsed.distanceMeters == null)
    }

    @Test
    fun `list ordering uses backend distance, not recomputed straight-line distance`() {
        val amenities = listOf(
            AmenityResponse(
                id = "a1",
                name = "Restroom A",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8965,
                longitude = -97.0438,
                distanceMeters = 50.0  // Backend shortest path distance
            ),
            AmenityResponse(
                id = "a2",
                name = "Restroom B",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8960,
                longitude = -97.0440,
                distanceMeters = 26.0  // Closer via corridor routing
            ),
            AmenityResponse(
                id = "a3",
                name = "Restroom C",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8970,
                longitude = -97.0435,
                distanceMeters = 100.0  // Farthest
            )
        )

        // Android should preserve backend order or sort by backend distance.
        // No straight-line haversine recomputation.
        val sorted = amenities.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }

        assertEquals("a2", sorted[0].id)  // Nearest: 26m
        assertEquals("a1", sorted[1].id)  // Middle: 50m
        assertEquals("a3", sorted[2].id)  // Farthest: 100m
    }

    @Test
    fun `multiple amenities preserve backend-provided distances without modification`() {
        val json = """
            [
              {
                "id": "m1",
                "name": "Men's Restroom 1",
                "amenityType": "MEN_RESTROOM",
                "level": "Level3",
                "latitude": 32.8965,
                "longitude": -97.0438,
                "distanceMeters": 26.0
              },
              {
                "id": "m2",
                "name": "Men's Restroom 2",
                "amenityType": "MEN_RESTROOM",
                "level": "Level3",
                "latitude": 32.8960,
                "longitude": -97.0445,
                "distanceMeters": 64.0
              },
              {
                "id": "m3",
                "name": "Men's Restroom 3",
                "amenityType": "MEN_RESTROOM",
                "level": "Level3",
                "latitude": 32.8970,
                "longitude": -97.0435,
                "distanceMeters": 103.0
              }
            ]
        """.trimIndent()

        val parsed = gson.fromJson(json, Array<AmenityResponse>::class.java).toList()

        assertEquals(3, parsed.size)
        assertEquals(26.0, parsed[0].distanceMeters!!, 0.001)
        assertEquals(64.0, parsed[1].distanceMeters!!, 0.001)
        assertEquals(103.0, parsed[2].distanceMeters!!, 0.001)

        // Confirm ordering is as returned by backend
        assertTrue(parsed[0].distanceMeters!! < parsed[1].distanceMeters!!)
        assertTrue(parsed[1].distanceMeters!! < parsed[2].distanceMeters!!)
    }
}
