package com.smartamenities.backend.routing;

import java.util.List;

/**
 * Ordered route guidance metadata derived from corridor geometry.
 */
public record RouteGuidancePlan(
        double totalDistanceMeters,
        List<String> routeSteps,
        List<RouteSegmentProgress> routeSegments,
        List<RouteStepProgressRange> stepProgressRanges
) {
    public RouteGuidancePlan {
        routeSteps = routeSteps == null ? List.of() : List.copyOf(routeSteps);
        routeSegments = routeSegments == null ? List.of() : List.copyOf(routeSegments);
        stepProgressRanges = stepProgressRanges == null ? List.of() : List.copyOf(stepProgressRanges);
    }
}
