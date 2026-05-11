package com.smartamenities.app.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressResponseParsingTest {

    private val gson = Gson()

    @Test
    fun `parses progress payload including arrived`() {
        val json = """
            {
              "snappedLatitude": 32.8984,
              "snappedLongitude": -97.0448,
              "clampedProgressMeters": 381.2,
              "activeStepIndex": 3,
              "activeInstruction": "Arrive at Men's Restroom.",
              "remainingDistanceMeters": 0.0,
              "remainingSteps": ["Arrive at Men's Restroom."],
              "arrived": true
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteProgressResponse::class.java)

        assertTrue(parsed.arrived == true)
        assertEquals(3, parsed.activeStepIndex)
        assertEquals("Arrive at Men's Restroom.", parsed.activeInstruction)
    }

    @Test
    fun `handles missing optional progress fields safely`() {
        val json = """
            {
              "snappedLatitude": 32.8974,
              "snappedLongitude": -97.0449
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteProgressResponse::class.java)

        assertEquals(32.8974, parsed.snappedLatitude!!, 0.0)
        assertEquals(-97.0449, parsed.snappedLongitude!!, 0.0)
        assertNull(parsed.activeStepIndex)
        assertNull(parsed.arrived)
    }

    @Test
    fun `parses arrived false without forcing completion`() {
        val json = """
            {
              "snappedLatitude": 32.8981,
              "snappedLongitude": -97.0446,
              "remainingDistanceMeters": 3.0,
              "activeStepIndex": 1,
              "arrived": false
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteProgressResponse::class.java)

        assertTrue(parsed.arrived == false)
        assertEquals(3.0, parsed.remainingDistanceMeters!!, 0.0)
        assertEquals(1, parsed.activeStepIndex)
    }
}

