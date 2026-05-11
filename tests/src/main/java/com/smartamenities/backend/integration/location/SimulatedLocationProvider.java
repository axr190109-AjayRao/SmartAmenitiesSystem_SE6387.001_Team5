package com.smartamenities.backend.integration.location;

import com.smartamenities.backend.model.StartLocation;
import com.smartamenities.backend.service.Level3SessionStartService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Simulated GPS location provider — resolves start location from seeded session or explicit request coordinates.
 */
@Service
@Primary
public class SimulatedLocationProvider implements LocationProvider {

    private final Level3SessionStartService level3SessionStartService;

    public SimulatedLocationProvider(Level3SessionStartService level3SessionStartService) {
        this.level3SessionStartService = level3SessionStartService;
    }

    @Override
    public StartLocation resolveStartLocation(
            Double currentLat,
            Double currentLon,
            String sessionSeed,
            StartLocation defaultStartLocation
    ) {
        // Explicit request coordinates (reroute from current position) must take precedence
        // over seeded session defaults.
        if (currentLat != null && currentLon != null) {
            return new StartLocation(
                    "Custom Start",
                    defaultStartLocation.getLevel(),
                    currentLat,
                    currentLon
            );
        }

        if (sessionSeed != null && !sessionSeed.isBlank()) {
            return level3SessionStartService.resolveStartLocation(sessionSeed);
        }

        return defaultStartLocation;
    }
}
