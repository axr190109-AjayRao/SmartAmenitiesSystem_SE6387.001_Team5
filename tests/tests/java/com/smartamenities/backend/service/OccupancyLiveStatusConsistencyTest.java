package com.smartamenities.backend.service;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.integration.occupancy.SimulatedAmenityOccupancyProvider;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.OccupancyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying that occupancy provider values are internally consistent —
 * stall counts, wait times, and occupancy status must agree with each other.
 */
class OccupancyLiveStatusConsistencyTest {

    @Test
    void stallsAvailableAndWaitTimeAreNonNegative() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, "consistency-bounds-seed"
        );

        for (AmenityService.AmenityDistance ad : results) {
            AmenityService.LiveStatus live = ad.liveStatus();
            assertTrue(live.stallsAvailable() >= 0,
                    "stallsAvailable must be >= 0 for " + ad.amenity().getId());
            assertTrue(live.waitTimeMinutes() >= 0,
                    "waitTimeMinutes must be >= 0 for " + ad.amenity().getId());
        }
    }

    @Test
    void fullOccupancyImpliesNoStallsOrLongWait() {
        SimulatedAmenityOccupancyProvider provider = new SimulatedAmenityOccupancyProvider();
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();

        // Sample every amenity across several seeds to collect FULL-status readings.
        String[] seeds = {"full-check-1", "full-check-2", "full-check-3", "full-check-peak"};
        for (Amenity amenity : loader.getAmenities()) {
            for (String seed : seeds) {
                AmenityLiveStatus status = provider.getLiveStatus(amenity, null, null, seed);
                if (status.occupancyStatus() == OccupancyStatus.FULL) {
                    assertTrue(
                        status.stallsAvailable() == 0 || status.waitTimeMinutes() >= 12,
                        "FULL occupancy must have 0 stalls or wait >= 12 min for amenity "
                        + amenity.getId() + " seed=" + seed
                    );
                }
            }
        }
    }

    @Test
    void lowOccupancyImpliesStallsAvailableAndShortWait() {
        SimulatedAmenityOccupancyProvider provider = new SimulatedAmenityOccupancyProvider();
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();

        String[] seeds = {"low-check-1", "low-check-2", "low-check-3", "low-check-off-peak"};
        for (Amenity amenity : loader.getAmenities()) {
            for (String seed : seeds) {
                AmenityLiveStatus status = provider.getLiveStatus(amenity, null, null, seed);
                if (status.occupancyStatus() == OccupancyStatus.LOW) {
                    assertTrue(status.stallsAvailable() > 2,
                            "LOW occupancy must have > 2 stalls for " + amenity.getId());
                    assertTrue(status.waitTimeMinutes() < 8,
                            "LOW occupancy should have < 8 min wait for " + amenity.getId());
                }
            }
        }
    }

    @Test
    void simulatedProviderAlwaysReturnsOpenStatus() {
        SimulatedAmenityOccupancyProvider provider = new SimulatedAmenityOccupancyProvider();
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();

        for (Amenity amenity : loader.getAmenities()) {
            AmenityLiveStatus status = provider.getLiveStatus(
                    amenity,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    "open-status-seed"
            );
            assertEquals(AmenityStatus.OPEN, status.status(),
                    "SimulatedAmenityOccupancyProvider should always return OPEN; "
                    + "closure is the responsibility of AmenityService");
        }
    }

    @Test
    void occupancyStatusMatchesStallsAndWaitCombination() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, "status-stalls-match-seed"
        );

        for (AmenityService.AmenityDistance ad : results) {
            AmenityService.LiveStatus live = ad.liveStatus();
            if (!live.isSelectable()) continue; // skip CLOSED entries

            switch (live.occupancyStatus()) {
                case HIGH -> assertTrue(live.stallsAvailable() <= 2 || live.waitTimeMinutes() >= 8,
                        "HIGH must have <=2 stalls or >=8 min wait");
                case FULL -> assertTrue(live.stallsAvailable() == 0 || live.waitTimeMinutes() >= 12,
                        "FULL must have 0 stalls or >=12 min wait");
                default -> { /* LOW and MEDIUM have no strict lower bound to check here */ }
            }
        }
    }
}
