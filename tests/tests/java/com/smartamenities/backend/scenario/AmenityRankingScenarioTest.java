package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.OccupancyStatus;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Amenity list is ranked so that distance is the primary factor,
 * with live status (wait time, stalls, occupancy) as secondary tiebreaker.
 */
class AmenityRankingScenarioTest {

    private static final double DISTANCE_DOMINANCE_THRESHOLD_METERS = 100.0;

    @Test
    void openAmenitiesRankedByDistanceWhenLiveStatusIsUniform() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        // Uniform occupancy ensures distance is the only differentiator.
        AmenityOccupancyProvider uniformProvider = (amenity, lat, lon, seed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 2, 5, OccupancyStatus.MEDIUM);

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, uniformProvider);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.WOMEN_RESTROOM,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "ranking-uniform-seed"
        );

        List<AmenityService.AmenityDistance> openResults = results.stream()
                .filter(ad -> ad.liveStatus().isSelectable())
                .toList();

        assertTrue(openResults.size() >= 2, "Need at least 2 open amenities to compare ranking");

        for (int i = 1; i < openResults.size(); i++) {
            Double prev = openResults.get(i - 1).distanceMeters();
            Double curr = openResults.get(i).distanceMeters();
            if (prev != null && curr != null) {
                assertTrue(prev <= curr + 0.1,
                        "With uniform live status, closer amenity should rank first: "
                        + prev + " > " + curr);
            }
        }
    }

    @Test
    void amenityMoreThan100mFartherNeverRanksAboveCloserOne() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        AmenityOccupancyProvider uniformProvider = (amenity, lat, lon, seed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 2, 5, OccupancyStatus.MEDIUM);

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, uniformProvider);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "ranking-dominance-seed"
        );

        List<AmenityService.AmenityDistance> open = results.stream()
                .filter(ad -> ad.liveStatus().isSelectable() && ad.distanceMeters() != null)
                .toList();

        for (int i = 0; i < open.size(); i++) {
            for (int j = i + 1; j < open.size(); j++) {
                double dI = open.get(i).distanceMeters();
                double dJ = open.get(j).distanceMeters();
                // j is ranked lower (farther in the sorted list); if it's more than 100m
                // closer than i, that would be a distance-dominance violation.
                if (dI - dJ > DISTANCE_DOMINANCE_THRESHOLD_METERS) {
                    fail(String.format(
                        "Amenity at index %d (dist=%.1fm) ranked before amenity at index %d (dist=%.1fm), "
                        + "but the latter is >100m closer — distance should dominate",
                        i, dI, j, dJ
                    ));
                }
            }
        }
    }

    @Test
    void closedAmenitiesAlwaysAtBottomRegardlessOfDistance() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "ranking-closed-last-seed"
        );

        boolean seenClosed = false;
        for (AmenityService.AmenityDistance ad : results) {
            if (!ad.liveStatus().isSelectable()) {
                seenClosed = true;
            } else {
                assertFalse(seenClosed,
                        "Open amenity found after a closed one — closed entries must be last");
            }
        }
    }
}
