package com.smartamenities.backend.integration.wayfinder;

/**
 * Boundary interface for external wayfinding request context.
 */
public interface WayfinderProvider {

    ResolvedWayfinderRequest resolveRequest(
            String destinationAmenityId,
            String destinationLabel,
            String currentLocation,
            String defaultCurrentLocation
    );
}
