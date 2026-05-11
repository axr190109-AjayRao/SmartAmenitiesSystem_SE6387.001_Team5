package com.smartamenities.backend.integration.occupancy;

import com.smartamenities.backend.model.Amenity;

/**
 * Boundary interface for external occupancy sensor lookups.
 */
public interface AmenityOccupancyProvider {

    AmenityLiveStatus getLiveStatus(Amenity amenity, Double currentLat, Double currentLon, String sessionSeed);
}
