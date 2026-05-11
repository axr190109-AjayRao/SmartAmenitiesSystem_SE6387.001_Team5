package com.smartamenities.backend.integration.wayfinder;

/**
 * Canonical request context used by core services independent of provider origin.
 */
public record ResolvedWayfinderRequest(
        String destinationAmenityId,
        String destinationLabel,
        String currentLocation
) {
}
