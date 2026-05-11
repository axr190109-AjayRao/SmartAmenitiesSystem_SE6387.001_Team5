package com.smartamenities.backend.routing;

/**
 * Cumulative route progress range for a single navigation instruction.
 */
public record RouteStepProgressRange(
        int stepIndex,
        double startMeters,
        double endMeters,
        String instruction
) {
}
