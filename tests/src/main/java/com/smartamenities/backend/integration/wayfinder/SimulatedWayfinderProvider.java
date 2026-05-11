package com.smartamenities.backend.integration.wayfinder;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Simulated DFW Wayfinder provider — in-process stand-in for the real Wayfinder integration.
 */
@Service
@Primary
public class SimulatedWayfinderProvider implements WayfinderProvider {

    @Override
    public ResolvedWayfinderRequest resolveRequest(
            String destinationAmenityId,
            String destinationLabel,
            String currentLocation,
            String defaultCurrentLocation
    ) {
        return new ResolvedWayfinderRequest(
                normalizeOptional(destinationAmenityId),
                normalizeOptional(destinationLabel),
                normalizeCurrentLocation(currentLocation, defaultCurrentLocation)
        );
    }

    private static String normalizeCurrentLocation(String currentLocation, String fallback) {
        String normalized = normalizeOptional(currentLocation);
        if (normalized == null) {
            return fallback;
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
