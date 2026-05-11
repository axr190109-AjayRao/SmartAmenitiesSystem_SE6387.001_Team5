package com.smartamenities.backend.integration;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: The same session seed produces repeatable results across calls,
 * while different seeds produce independent, distinct results.
 */
class SessionSeedConsistencyIntegrationTest {

    @Test
    void sameSeedProducesSameTopAmenityAndDistance() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String seed = "consistent-seed-xyz";

        List<AmenityService.AmenityDistance> first = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.WOMEN_RESTROOM, null, null, false, seed
        );
        List<AmenityService.AmenityDistance> second = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.WOMEN_RESTROOM, null, null, false, seed
        );

        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertEquals(first.get(0).amenity().getId(), second.get(0).amenity().getId());
        assertEquals(first.get(0).distanceMeters(), second.get(0).distanceMeters(), 0.001);
    }

    @Test
    void sameSeedProducesConsistentRouteStart() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        String seed = "route-seed-consistency";
        var destination = loader.getAmenities().get(0);

        Route first = routeService.generateRoute(
                new RouteRequest(destination.getDisplayName(), "start", false, destination.getId(), null, null),
                seed
        );
        Route second = routeService.generateRoute(
                new RouteRequest(destination.getDisplayName(), "start", false, destination.getId(), null, null),
                seed
        );

        assertEquals(first.getStartLatitude(), second.getStartLatitude(), 0.000001);
        assertEquals(first.getStartLongitude(), second.getStartLongitude(), 0.000001);
        assertEquals(first.getTotalDistanceMeters(), second.getTotalDistanceMeters(), 0.001);
    }

    @Test
    void differentSeedsMayProduceDifferentRankings() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        // Use several seed pairs; at least one should differ in which amenity is forced closed.
        String[] seeds = {"seed-A", "seed-B", "seed-C", "seed-D"};
        boolean foundDifference = false;

        String firstClosedId = null;
        for (String seed : seeds) {
            List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                    AmenityType.MEN_RESTROOM, null, null, false, seed
            );
            String closedId = results.stream()
                    .filter(ad -> !ad.liveStatus().isSelectable())
                    .map(ad -> ad.amenity().getId())
                    .findFirst()
                    .orElse(null);

            if (firstClosedId == null) {
                firstClosedId = closedId;
            } else if (!firstClosedId.equals(closedId)) {
                foundDifference = true;
                break;
            }
        }

        assertTrue(foundDifference,
                "Different session seeds should produce different closed amenity selections");
    }
}
