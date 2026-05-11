package com.smartamenities.app.ui.navigation

import com.smartamenities.app.model.RouteProgressResponse

internal object RouteArrivalDecider {

    private const val ARRIVAL_EPSILON_METERS = 0.5
    private const val FALLBACK_COMPLETION_METERS = 2.0

    fun isArrived(
        progress: RouteProgressResponse,
        totalDistanceMeters: Double?,
        lastStepIndex: Int?
    ): Boolean {
        // Backend arrived is source-of-truth when present.
        if (progress.arrived == true) {
            return true
        }

        val remaining = progress.remainingDistanceMeters
        if (remaining != null && remaining <= FALLBACK_COMPLETION_METERS) {
            return true
        }

        val activeIndex = progress.activeStepIndex
        if (activeIndex != null && lastStepIndex != null && activeIndex >= lastStepIndex) {
            return true
        }

        val clamped = progress.clampedProgressMeters
        if (clamped != null && totalDistanceMeters != null && clamped >= (totalDistanceMeters - ARRIVAL_EPSILON_METERS)) {
            return true
        }

        return false
    }
}