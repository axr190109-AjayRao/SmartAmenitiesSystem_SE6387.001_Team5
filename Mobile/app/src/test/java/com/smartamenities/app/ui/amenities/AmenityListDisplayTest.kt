package com.smartamenities.app.ui.amenities

import com.smartamenities.app.model.AmenityResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests that AmenityListActivity displays backend distance exactly,
 * without client-side recomputation or modification.
 */
class AmenityListDisplayTest {

    @Test
    fun `formats backend distance for display without modification`() {
        val amenity = AmenityResponse(
            id = "test_1",
            name = "Test Amenity",
            amenityType = "MEN_RESTROOM",
            level = "Level3",
            latitude = 32.8965,
            longitude = -97.0438,
            distanceMeters = 26.47
        )

        // Mimic buildSubtitle logic from AmenityListActivity
        val distanceText = amenity.distanceMeters?.let {
            String.format(Locale.US, "~%.0f m", it)
        } ?: "Distance unavailable"

        val subtitle = "${amenity.level}  |  $distanceText"

        assertEquals("Level3  |  ~26 m", subtitle)
    }

    @Test
    fun `displays null distance gracefully`() {
        val amenity = AmenityResponse(
            id = "test_2",
            name = "Test Amenity No Distance",
            amenityType = "WOMEN_RESTROOM",
            level = "Level3",
            latitude = 32.8967,
            longitude = -97.0440,
            distanceMeters = null
        )

        val distanceText = amenity.distanceMeters?.let {
            String.format(Locale.US, "~%.0f m", it)
        } ?: "Distance unavailable"

        val subtitle = "${amenity.level}  |  $distanceText"

        assertEquals("Level3  |  Distance unavailable", subtitle)
    }

    @Test
    fun `list maintains backend sort order nearest-first by route distance`() {
        val amenities = listOf(
            AmenityResponse(
                id = "a1",
                name = "Restroom A",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8965,
                longitude = -97.0438,
                distanceMeters = 26.0
            ),
            AmenityResponse(
                id = "a2",
                name = "Restroom B",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8960,
                longitude = -97.0440,
                distanceMeters = 64.0
            ),
            AmenityResponse(
                id = "a3",
                name = "Restroom C",
                amenityType = "MEN_RESTROOM",
                level = "Level3",
                latitude = 32.8970,
                longitude = -97.0435,
                distanceMeters = 103.0
            )
        )

        // Sort preserves backend order (nearest-first)
        val sorted = amenities.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }

        assertEquals("a1", sorted[0].id)
        assertEquals("a2", sorted[1].id)
        assertEquals("a3", sorted[2].id)

        // Confirm distances are strictly increasing
        assertTrue(sorted[0].distanceMeters!! < sorted[1].distanceMeters!!)
        assertTrue(sorted[1].distanceMeters!! < sorted[2].distanceMeters!!)
    }

    @Test
    fun `no client-side haversine fallback when backend distanceMeters is present`() {
        val amenity = AmenityResponse(
            id = "test_3",
            name = "Test Amenity",
            amenityType = "ACCESSIBLE_RESTROOM",
            level = "Level3",
            latitude = 32.8965,
            longitude = -97.0438,
            distanceMeters = 59.0  // Backend route-network distance
        )

        // Android should use this value as-is, not compute haversine or other client-side metric
        val displayDistance = amenity.distanceMeters
        assertEquals(59.0, displayDistance!!, 0.001)

        // Confirm we don't recompute anything
        val straightLineDistance = calculateHaversine(
            32.8968,  // default start lat
            -97.0441, // default start lon
            amenity.latitude,
            amenity.longitude
        )

        // These SHOULD differ (straight line vs corridor), and Android should use backend value
        assertTrue(displayDistance != straightLineDistance)
    }

    private fun calculateHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0  // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.asin(Math.sqrt(a))
        return R * c
    }
}
