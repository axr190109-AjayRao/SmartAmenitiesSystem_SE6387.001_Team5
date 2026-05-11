package com.smartamenities.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNavigationMapperTest {

    @Test
    fun `metadata step mapping does not force segments onto terminal arrival step`() {
        val route = RouteResponse(
            destination = "Women's Restroom",
            accessibilityOn = true,
            estimatedTime = "5 min",
            routeSteps = listOf(
                RouteStepResponse("Proceed forward 140 meters."),
                RouteStepResponse("Turn slight left and continue 25 meters."),
                RouteStepResponse("Arrive at Women's Restroom.")
            ),
            startLocationName = "Terminal D Level 3 starting point",
            totalDistanceMeters = 165.0,
            routeSegments = listOf(
                RouteSegmentResponse(0, 32.0, -97.0, 32.1, -97.1, 140.0, 0.0, 140.0, 0),
                RouteSegmentResponse(1, 32.1, -97.1, 32.2, -97.2, 25.0, 140.0, 165.0, 1)
            ),
            stepProgressRanges = listOf(
                RouteStepProgressRangeResponse(0, 0.0, 140.0, "Proceed forward 140 meters."),
                RouteStepProgressRangeResponse(1, 140.0, 165.0, "Turn slight left and continue 25 meters.")
            ),
            routeGeoPoints = listOf(
                RouteGeoPoint(longitude = -97.0, latitude = 32.0),
                RouteGeoPoint(longitude = -97.2, latitude = 32.2)
            )
        )

        val model = route.toNavigationModel()

        assertEquals(2, model.terminalStepIndex)
        assertEquals(1, model.maxProgressStepIndex)
        assertEquals(
            1,
            model.resolveActiveStepIndex(
                progress = RouteProgressResponse(activeStepIndex = 2, clampedProgressMeters = 160.0),
                arrivalReached = false
            )
        )
        assertEquals(
            2,
            model.resolveActiveStepIndex(
                progress = RouteProgressResponse(activeStepIndex = 1, clampedProgressMeters = 165.0, arrived = true),
                arrivalReached = true
            )
        )
    }

    @Test
    fun `progress metadata is false when optional lists are empty`() {
        val route = RouteResponse(
            destination = "Men's Restroom",
            accessibilityOn = false,
            estimatedTime = "3 min",
            routeSteps = listOf(RouteStepResponse("Arrive at Men's Restroom.")),
            startLocationName = "Terminal D Level 3",
            totalDistanceMeters = 30.0,
            routeSegments = emptyList(),
            stepProgressRanges = emptyList(),
            routeGeoPoints = listOf(RouteGeoPoint(longitude = -97.0, latitude = 32.0))
        )

        val model = route.toNavigationModel()

        assertFalse(model.hasProgressMetadataForSimulation())
        assertNull(
            model.resolveActiveStepIndex(
                progress = RouteProgressResponse(activeStepIndex = null, clampedProgressMeters = null),
                arrivalReached = false
            )
        )
    }

    @Test
    fun `uses step progress ranges to resolve active step when backend index is missing`() {
        val route = RouteResponse(
            destination = "Accessible Restroom",
            accessibilityOn = true,
            estimatedTime = "4 min",
            routeSteps = listOf(
                RouteStepResponse("Proceed forward 50 meters."),
                RouteStepResponse("Turn right and continue 20 meters."),
                RouteStepResponse("Arrive at Accessible Restroom.")
            ),
            startLocationName = "Terminal D Level 3",
            totalDistanceMeters = 70.0,
            stepProgressRanges = listOf(
                RouteStepProgressRangeResponse(0, 0.0, 50.0, "Proceed forward 50 meters."),
                RouteStepProgressRangeResponse(1, 50.0, 70.0, "Turn right and continue 20 meters.")
            ),
            routeGeoPoints = listOf(
                RouteGeoPoint(longitude = -97.0, latitude = 32.0),
                RouteGeoPoint(longitude = -97.1, latitude = 32.1)
            )
        )

        val model = route.toNavigationModel()

        assertTrue(model.hasProgressMetadataForSimulation())
        assertEquals(
            1,
            model.resolveActiveStepIndex(
                progress = RouteProgressResponse(activeStepIndex = null, clampedProgressMeters = 68.0),
                arrivalReached = false
            )
        )
    }
}

