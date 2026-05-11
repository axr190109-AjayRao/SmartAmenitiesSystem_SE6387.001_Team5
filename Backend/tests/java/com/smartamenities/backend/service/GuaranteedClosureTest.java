package com.smartamenities.backend.service;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.OccupancyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the guaranteed-closure post-processing in AmenityService.
 * Every call to getAmenitiesWithRouteDistance must return at least one non-selectable entry.
 */
class GuaranteedClosureTest {

    @Test
    void allAmenityListsHaveAtLeastOneClosedEntry() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        // Use a provider that always returns OPEN so we can confirm the service itself forces closure.
        AmenityOccupancyProvider alwaysOpenProvider = (amenity, lat, lon, seed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 2, 5, OccupancyStatus.LOW);

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, alwaysOpenProvider);

        for (AmenityType type : AmenityType.values()) {
            List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                    type, null, null, false, "guaranteed-closure-" + type.name()
            );

            long closedCount = results.stream()
                    .filter(ad -> !ad.liveStatus().isSelectable())
                    .count();

            assertTrue(closedCount >= 1,
                    "Type " + type + " should have at least one closed amenity; got " + closedCount);
        }
    }

    @Test
    void guaranteedClosureIsConsistentForSameSeed() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        AmenityOccupancyProvider alwaysOpenProvider = (amenity, lat, lon, seed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 0, 5, OccupancyStatus.LOW);

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, alwaysOpenProvider);

        String seed = "closure-consistency-seed";

        List<AmenityService.AmenityDistance> first = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM, null, null, false, seed
        );
        List<AmenityService.AmenityDistance> second = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM, null, null, false, seed
        );

        String closedIdFirst = first.stream()
                .filter(ad -> !ad.liveStatus().isSelectable())
                .map(ad -> ad.amenity().getId())
                .findFirst()
                .orElse(null);

        String closedIdSecond = second.stream()
                .filter(ad -> !ad.liveStatus().isSelectable())
                .map(ad -> ad.amenity().getId())
                .findFirst()
                .orElse(null);

        assertNotNull(closedIdFirst);
        assertEquals(closedIdFirst, closedIdSecond,
                "Same session seed should always force the same amenity closed");
    }

    @Test
    void closureWorksWithNullSessionSeed() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        AmenityOccupancyProvider alwaysOpenProvider = (amenity, lat, lon, seed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 0, 5, OccupancyStatus.LOW);

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, alwaysOpenProvider);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, null
        );

        long closedCount = results.stream()
                .filter(ad -> !ad.liveStatus().isSelectable())
                .count();

        assertTrue(closedCount >= 1,
                "Guaranteed closure should work even with null session seed");
    }
}
