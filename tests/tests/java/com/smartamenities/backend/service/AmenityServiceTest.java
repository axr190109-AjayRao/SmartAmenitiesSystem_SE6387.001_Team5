package com.smartamenities.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.OccupancyStatus;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.routing.GeoMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmenityServiceTest {

    private static double polylineDistanceMeters(List<RouteGeoPoint> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double distance = 0.0;
        for (int i = 1; i < points.size(); i++) {
            RouteGeoPoint previous = points.get(i - 1);
            RouteGeoPoint current = points.get(i);
            distance += GeoMath.haversineMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude()
            );
        }
        return distance;
    }

    @Test
    void filtersByAmenityType() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService service = TestServiceFactory.amenityService(loader, routeMetricsService);

        List<AmenityService.AmenityDistance> women = service.getAmenitiesWithRouteDistance(AmenityType.WOMEN_RESTROOM, null, null, false);

        assertFalse(women.isEmpty());
        assertTrue(women.stream().allMatch(entry -> entry.amenity().getAmenityType() == AmenityType.WOMEN_RESTROOM));
    }

    @Test
    void ranksNearestAmenitiesUsingRouteDistanceWhenCurrentLocationProvided() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService service = TestServiceFactory.amenityService(loader, routeMetricsService);

        double currentLat = loader.getStartLocation().getLatitude();
        double currentLon = loader.getStartLocation().getLongitude();

        List<AmenityService.AmenityDistance> results = service.getAmenitiesWithRouteDistance(
                AmenityType.WOMEN_RESTROOM,
                currentLat,
                currentLon,
                false
        );

        assertTrue(results.size() >= 2);
        assertNotNull(results.get(0).distanceMeters());
        assertNotNull(results.get(1).distanceMeters());

        assertTrue(results.get(0).distanceMeters() <= results.get(1).distanceMeters());
    }

    @Test
    void amenityListDistanceMatchesRouteDistanceForSameDestination() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
        RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        double currentLat = loader.getStartLocation().getLatitude();
        double currentLon = loader.getStartLocation().getLongitude();
        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.ACCESSIBLE_RESTROOM,
                currentLat,
                currentLon,
                false
        );

        AmenityService.AmenityDistance destination = results.stream()
            .filter(entry -> entry.distanceMeters() != null && entry.distanceMeters() > 10.0)
            .findFirst()
            .orElse(results.get(0));
        Route route = routeService.generateRoute(new RouteRequest(
                destination.amenity().getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                destination.amenity().getId(),
                null,
                null
        ));

        double routeDistance = polylineDistanceMeters(route.getRouteGeoPoints());
        assertNotNull(destination.distanceMeters());
        assertEquals(destination.distanceMeters(), routeDistance, 2.0);
    }

    @Test
    void missingCurrentCoordinatesUseSameDefaultStartAsRoute() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);

        List<AmenityService.AmenityDistance> withDefaults = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM,
                null,
                null,
                false
        );

        List<AmenityService.AmenityDistance> withExplicitStart = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false
        );

        assertFalse(withDefaults.isEmpty());
        assertFalse(withExplicitStart.isEmpty());
        assertEquals(withDefaults.get(0).amenity().getId(), withExplicitStart.get(0).amenity().getId());
        assertEquals(withDefaults.get(0).distanceMeters(), withExplicitStart.get(0).distanceMeters(), 1.0);
    }

    @Test
    void accessibilityModeDoesNotBreakDistanceComputation() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        Amenity amenity = loader.getAmenities().get(0);

        double standardDistance = routeMetricsService
                .computeRouteMetrics(amenity, false, null, null)
                .pathResult()
                .totalDistanceMeters();
        double accessibleDistance = routeMetricsService
                .computeRouteMetrics(amenity, true, null, null)
                .pathResult()
                .totalDistanceMeters();

        assertTrue(accessibleDistance >= standardDistance - 0.001);
    }

    @Test
    void seededSessionUsesSameStartForAmenitiesAndRoutes() {
    GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
    RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
    AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
    RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

    String sessionSeed = "session-alpha";
    Amenity destination = loader.getAmenities().get(0);

    RouteMetricsService.RouteComputation routeComputation = routeMetricsService.computeRouteMetrics(
        destination,
        true,
        null,
        null,
        sessionSeed
    );
    Route route = routeService.generateRoute(
        new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null),
        sessionSeed
    );

    assertNotNull(routeComputation.startLocation());
    assertEquals(routeComputation.startLocation().getLatitude(), route.getStartLatitude(), 0.000001);
    assertEquals(routeComputation.startLocation().getLongitude(), route.getStartLongitude(), 0.000001);
    assertTrue(route.getStartLocationName().contains("Session Start") || route.getStartLocationName().equals(loader.getStartLocation().getName()));

    List<AmenityService.AmenityDistance> firstAmenities = amenityService.getAmenitiesWithRouteDistance(
        AmenityType.MEN_RESTROOM,
        null,
        null,
        false,
        sessionSeed
    );
    List<AmenityService.AmenityDistance> secondAmenities = amenityService.getAmenitiesWithRouteDistance(
        AmenityType.MEN_RESTROOM,
        null,
        null,
        false,
        sessionSeed
    );

    assertFalse(firstAmenities.isEmpty());
    assertFalse(secondAmenities.isEmpty());
    assertEquals(firstAmenities.get(0).amenity().getId(), secondAmenities.get(0).amenity().getId());
    assertEquals(firstAmenities.get(0).distanceMeters(), secondAmenities.get(0).distanceMeters(), 0.000001);
    }

    @Test
    void closurePopupTriggerFiresExactlyOnceWithinEarlyMidLateWindow() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);

        String amenityId = loader.getAmenities().get(0).getId();
        String sessionSeed = "closure-seed-" + System.nanoTime();
        int closedCount = 0;
        int firstClosedPoll = -1;

        for (int poll = 1; poll <= 20; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.CLOSED) {
                closedCount++;
                if (firstClosedPoll < 0) {
                    firstClosedPoll = poll;
                }
            }
        }

        assertEquals(1, closedCount);
        assertTrue(firstClosedPoll >= 1 && firstClosedPoll <= 12);
    }

    @Test
    void replacementRecommendationUsesPassengerCurrentLocation() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityOccupancyProvider alwaysOpenProvider = (amenity, currentLat, currentLon, sessionSeed) ->
                new AmenityLiveStatus(AmenityStatus.OPEN, null, 0, 10, OccupancyStatus.LOW);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService, alwaysOpenProvider);

        Amenity closedAmenity = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow();
        Amenity farMenAmenity = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .filter(a -> !a.getId().equals(closedAmenity.getId()))
                .reduce((first, second) -> second)
                .orElseThrow();

        AmenityService.AmenityRecommendation recommendation = amenityService.findNearestOpenAmenitySameType(
                closedAmenity.getId(),
                farMenAmenity.getLatitude(),
                farMenAmenity.getLongitude(),
                false,
                "replacement-loc-seed"
        );

        assertEquals(farMenAmenity.getId(), recommendation.amenity().getId());
    }
}
