package com.smartamenities.backend.routing;

/**
 * Per-segment route progress metadata.
 */
public record RouteSegmentProgress(
        int segmentIndex,
        double fromLat,
        double fromLon,
        double toLat,
        double toLon,
        double lengthMeters,
        double cumulativeStartMeters,
        double cumulativeEndMeters,
        int stepIndex
) {
}
