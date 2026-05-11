package com.smartamenities.app.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSeedForwardingContractTest {

    private val gson = Gson()

    @Test
    fun `route request serializes optional sessionSeed`() {
        val request = RouteRequest(
            destination = "Men's Restroom",
            currentLocation = "Terminal D Level 3 starting point",
            accessibilityOn = false,
            destinationAmenityId = "amenity_men_3_01",
            sessionSeed = "session-seed-123"
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"sessionSeed\":\"session-seed-123\""))
    }

    @Test
    fun `route request keeps backward compatibility when sessionSeed missing`() {
        val json = """
            {
              "destination": "Women's Restroom",
              "currentLocation": "Terminal D Level 3 starting point",
              "accessibilityOn": true,
              "destinationAmenityId": "amenity_women_3_02"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteRequest::class.java)

        assertEquals("Women's Restroom", parsed.destination)
        assertEquals("Terminal D Level 3 starting point", parsed.currentLocation)
        assertEquals(true, parsed.accessibilityOn)
        assertEquals("amenity_women_3_02", parsed.destinationAmenityId)
        assertNull(parsed.sessionSeed)
    }
}

