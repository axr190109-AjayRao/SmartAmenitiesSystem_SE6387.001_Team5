package com.smartamenities.app.ui.navigation

import com.smartamenities.app.model.RouteProgressResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteArrivalDeciderTest {

    @Test
    fun `arrived true from backend is source of truth`() {
        val progress = RouteProgressResponse(arrived = true, remainingDistanceMeters = 5.0, activeStepIndex = 1)

        assertTrue(RouteArrivalDecider.isArrived(progress, totalDistanceMeters = 100.0, lastStepIndex = 3))
    }

    @Test
    fun `missing arrived falls back to remaining distance threshold`() {
        val progress = RouteProgressResponse(arrived = null, remainingDistanceMeters = 1.5, activeStepIndex = 1)

        assertTrue(RouteArrivalDecider.isArrived(progress, totalDistanceMeters = 100.0, lastStepIndex = 3))
    }

    @Test
    fun `missing arrived falls back to last step index`() {
        val progress = RouteProgressResponse(arrived = null, remainingDistanceMeters = 8.0, activeStepIndex = 4)

        assertTrue(RouteArrivalDecider.isArrived(progress, totalDistanceMeters = 100.0, lastStepIndex = 4))
    }

    @Test
    fun `about three meters remaining is not premature arrival but next update can complete`() {
        val nearButNotArrived = RouteProgressResponse(arrived = null, remainingDistanceMeters = 3.0, activeStepIndex = 1)
        val arrivedNextTick = RouteProgressResponse(arrived = null, remainingDistanceMeters = 0.8, activeStepIndex = 2)

        assertFalse(RouteArrivalDecider.isArrived(nearButNotArrived, totalDistanceMeters = 120.0, lastStepIndex = 3))
        assertTrue(RouteArrivalDecider.isArrived(arrivedNextTick, totalDistanceMeters = 120.0, lastStepIndex = 3))
    }
}
