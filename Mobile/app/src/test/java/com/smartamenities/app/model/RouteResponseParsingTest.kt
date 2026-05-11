package com.smartamenities.app.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResponseParsingTest {

    private val gson = Gson()

    @Test
    fun `parses routeSteps object array and preserves order`() {
        val json = """
            {
              "destination": "Men's Restroom",
              "accessibilityOn": false,
              "estimatedTime": "6 min",
              "routeSteps": [
                {"instruction": "Proceed forward 120 meters."},
                {"instruction": "Slight left and continue 45 meters."},
                {"instruction": "Arrive at Men's Restroom."}
              ],
              "startLocationName": "Terminal D Level 3",
              "routeGeoPoints": [
                {"latitude": 32.8968, "longitude": -97.0441},
                {"latitude": 32.8969, "longitude": -97.0439}
              ]
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteResponse::class.java)

        assertEquals(
            listOf(
                "Proceed forward 120 meters.",
                "Slight left and continue 45 meters.",
                "Arrive at Men's Restroom."
            ),
            parsed.orderedRouteInstructions()
        )
    }

    @Test
    fun `handles missing routeSteps safely`() {
        val json = """
            {
              "destination": "Women's Restroom",
              "accessibilityOn": true,
              "estimatedTime": "4 min",
              "startLocationName": "Terminal D Level 3",
              "routeGeoPoints": [
                {"latitude": 32.8968, "longitude": -97.0441},
                {"latitude": 32.8967, "longitude": -97.0440}
              ]
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteResponse::class.java)

        assertTrue(parsed.orderedRouteInstructions().isEmpty())
        assertEquals(2, parsed.routeGeoPoints.orEmpty().size)
    }

    @Test
    fun `parses optional progress metadata`() {
        val json = """
            {
              "destination": "Accessible Restroom",
              "accessibilityOn": true,
              "estimatedTime": "4 min",
              "routeSteps": [
                {"instruction": "Proceed forward 20 meters."},
                {"instruction": "Arrive at Accessible Restroom."}
              ],
              "startLocationName": "Terminal D Level 3 starting point",
              "totalDistanceMeters": 24.4,
              "routeSegments": [
                {
                  "segmentIndex": 0,
                  "fromLat": 32.897485,
                  "fromLon": -97.044959,
                  "toLat": 32.897705,
                  "toLon": -97.044963,
                  "lengthMeters": 24.4,
                  "cumulativeStartMeters": 0.0,
                  "cumulativeEndMeters": 24.4,
                  "stepIndex": 0
                }
              ],
              "stepProgressRanges": [
                {
                  "stepIndex": 0,
                  "startMeters": 0.0,
                  "endMeters": 24.4,
                  "instruction": "Proceed forward 20 meters."
                }
              ],
              "routeGeoPoints": [
                {"latitude": 32.897485, "longitude": -97.044959},
                {"latitude": 32.897705, "longitude": -97.044963}
              ]
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteResponse::class.java)

        assertEquals(24.4, parsed.totalDistanceMeters!!, 0.0)
        assertEquals(1, parsed.routeSegments.orEmpty().size)
        assertEquals(1, parsed.stepProgressRanges.orEmpty().size)
        assertTrue(parsed.hasProgressMetadata())
    }

    @Test
    fun `handles empty route segment and progress metadata lists safely`() {
        val json = """
            {
              "destination": "Accessible Restroom",
              "accessibilityOn": true,
              "estimatedTime": "4 min",
              "routeSteps": [
                {"instruction": "Arrive at Accessible Restroom."}
              ],
              "startLocationName": "Terminal D Level 3 starting point",
              "totalDistanceMeters": 5.0,
              "routeSegments": [],
              "stepProgressRanges": [],
              "routeGeoPoints": [
                {"latitude": 32.897485, "longitude": -97.044959}
              ]
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, RouteResponse::class.java)

        assertTrue(parsed.routeSegments.orEmpty().isEmpty())
        assertTrue(parsed.stepProgressRanges.orEmpty().isEmpty())
        assertTrue(parsed.routeGeoPoints.orEmpty().size == 1)
        assertTrue(!parsed.hasProgressMetadata())
    }
}
