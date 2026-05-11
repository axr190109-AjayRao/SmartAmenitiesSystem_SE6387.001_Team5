package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: The amenity selection screen always displays at least one non-selectable (closed) entry
 * so passengers can see the closure and understand they must choose an alternative.
 */
class ClosedAmenityListScenarioTest {

    @Test
    void amenityListAlwaysContainsAtLeastOneClosedEntry() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, "closed-list-seed-a"
        );

        long closedCount = results.stream()
                .filter(ad -> ad.liveStatus().status() == AmenityStatus.CLOSED)
                .count();

        assertTrue(closedCount >= 1,
                "At least one amenity must be CLOSED; total=" + results.size() + " closed=" + closedCount);
    }

    @Test
    void closedEntriesAreRankedAfterOpenEntries() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM, null, null, false, "closed-rank-seed"
        );

        int firstClosedIndex = -1;
        int lastOpenIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).liveStatus().status() == AmenityStatus.CLOSED && firstClosedIndex < 0) {
                firstClosedIndex = i;
            }
            if (results.get(i).liveStatus().status() == AmenityStatus.OPEN) {
                lastOpenIndex = i;
            }
        }

        if (firstClosedIndex >= 0 && lastOpenIndex >= 0) {
            assertTrue(lastOpenIndex < firstClosedIndex,
                    "All open entries should appear before closed entries in the list");
        }
    }

    @Test
    void closedAmenityGuaranteeHoldsForMultipleSessions() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String[] seeds = {"session-alpha", "session-beta", "session-gamma", "session-delta", "session-epsilon"};

        for (String seed : seeds) {
            List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                    null, null, null, false, seed
            );
            long closedCount = results.stream()
                    .filter(ad -> ad.liveStatus().status() == AmenityStatus.CLOSED)
                    .count();
            assertTrue(closedCount >= 1,
                    "Session '" + seed + "' should have at least one closed amenity");
        }
    }
}
