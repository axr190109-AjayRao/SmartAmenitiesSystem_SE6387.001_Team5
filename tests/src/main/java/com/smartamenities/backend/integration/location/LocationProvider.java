package com.smartamenities.backend.integration.location;

import com.smartamenities.backend.model.StartLocation;

/**
 * Boundary interface for location provider integrations.
 */
public interface LocationProvider {

    StartLocation resolveStartLocation(
            Double currentLat,
            Double currentLon,
            String sessionSeed,
            StartLocation defaultStartLocation
    );
}
