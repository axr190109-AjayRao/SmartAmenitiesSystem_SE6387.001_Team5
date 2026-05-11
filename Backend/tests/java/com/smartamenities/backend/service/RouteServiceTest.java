package com.smartamenities.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.routing.RouteNetworkRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteServiceTest {

    @Test
    void routeStepsAreUniqueByDestinationAmenity() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
                RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
                AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
                RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        Amenity firstAmenity = loader.getAmenities().get(0);
        Amenity secondAmenity = loader.getAmenities().stream()
                .filter(a -> !a.getId().equals(firstAmenity.getId()))
                .findFirst()
                .orElseThrow();

        Route firstRoute = routeService.generateRoute(
                new RouteRequest("Restroom", "Terminal D Entrance", true, firstAmenity.getId(), null, null)
        );
        Route secondRoute = routeService.generateRoute(
                new RouteRequest("Restroom", "Terminal D Entrance", true, secondAmenity.getId(), null, null)
        );

        assertNotEquals(firstRoute.getRouteSteps(), secondRoute.getRouteSteps());
        assertTrue(firstRoute.getEstimatedTime().endsWith(" min"));
        assertTrue(secondRoute.getEstimatedTime().endsWith(" min"));
    }

    @Test
    void generatedRouteUsesSnappedStartAsFirstGeoPoint() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
                RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
                AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
                RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        Amenity destination = loader.getAmenities().get(0);
        Route route = routeService.generateRoute(
                new RouteRequest("Restroom", "Terminal D Level 3 starting point", false, destination.getId(), null, null)
        );

        List<RouteGeoPoint> routeGeoPoints = route.getRouteGeoPoints();
        assertNotNull(routeGeoPoints);
        assertTrue(routeGeoPoints.size() >= 2);

        RouteGeoPoint first = routeGeoPoints.get(0);
        assertEquals(route.getStartLatitude(), first.getLatitude(), 0.0000001);
        assertEquals(route.getStartLongitude(), first.getLongitude(), 0.0000001);

        RouteStartSnapper.SnapResult snappedAgain = RouteStartSnapper.snapToNearestSegment(
                first.getLatitude(),
                first.getLongitude(),
                loader.getWalkableRouteSegments()
        );
        assertTrue(snappedAgain.snapDistanceMeters() < 0.2);
    }

    @Test
    void rerouteRequestReturnsStartAtProvidedDeviationCoordinates() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
        RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        Amenity destination = loader.getAmenities().get(0);
        double deviationLat = 32.8995;
        double deviationLon = -97.04496;

        Route route = routeService.generateRoute(
                new RouteRequest(
                        destination.getDisplayName(),
                        "32.8995,-97.04496",
                        false,
                        destination.getId(),
                        deviationLat,
                        deviationLon
                )
        );

        assertEquals(deviationLat, route.getStartLatitude(), 0.0000001);
        assertEquals(deviationLon, route.getStartLongitude(), 0.0000001);
    }

    @Test
    void routeSupportsDestinationAmenityIdPresentAndAbsent() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
                RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
                AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
                RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        Amenity destination = loader.getAmenities().get(0);

        Route withId = routeService.generateRoute(
                new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", false, destination.getId(), null, null)
        );
        Route withoutId = routeService.generateRoute(
                new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", false, null, null, null)
        );

        assertNotNull(withId.getRouteGeoPoints());
        assertNotNull(withoutId.getRouteGeoPoints());
        assertFalse(withId.getRouteGeoPoints().isEmpty());
        assertFalse(withoutId.getRouteGeoPoints().isEmpty());
    }

    @Test
    void routePointsStayOnWalkableNetwork() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
                RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
                AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
                RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

        Amenity destination = loader.getAmenities().get(0);
        Route route = routeService.generateRoute(
                new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null)
        );

        RouteNetworkRouter.Graph graph = loader.getWalkableGraph();
        List<RouteGeoPoint> points = route.getRouteGeoPoints();
        assertNotNull(points);
        assertFalse(points.isEmpty());

        for (RouteGeoPoint point : points) {
            double distanceMeters = RouteNetworkRouter.distanceToGraph(graph, point);
            assertTrue(distanceMeters < 0.7);
        }
    }

        @Test
        void generatedRouteIncludesProgressMetadata() {
                GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
                RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
                AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
                RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);

                Amenity destination = loader.getAmenities().get(0);
                Route route = routeService.generateRoute(
                                new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null)
                );

                assertNotNull(route.getTotalDistanceMeters());
                assertTrue(route.getTotalDistanceMeters() > 0.0);
                assertNotNull(route.getRouteSegments());
                assertNotNull(route.getStepProgressRanges());
                assertNotNull(route.getRouteSteps());
                assertFalse(route.getRouteSegments().isEmpty());
                assertFalse(route.getStepProgressRanges().isEmpty());
                assertFalse(route.getRouteSteps().isEmpty());

                String lastRouteStep = route.getRouteSteps().get(route.getRouteSteps().size() - 1).toLowerCase();
                assertTrue(lastRouteStep.startsWith("arrive at"));
                assertEquals(route.getRouteSteps().size(), route.getStepProgressRanges().size());

                double previousEndMeters = 0.0;
                for (int index = 0; index < route.getRouteSegments().size(); index++) {
                        var segment = route.getRouteSegments().get(index);
                        assertEquals(index, segment.segmentIndex());
                        assertTrue(segment.lengthMeters() > 0.0);
                        assertTrue(segment.cumulativeEndMeters() >= segment.cumulativeStartMeters());
                        assertTrue(segment.cumulativeStartMeters() >= previousEndMeters - 0.01);
                        assertTrue(segment.stepIndex() >= 0);
                        assertTrue(segment.stepIndex() < route.getRouteSteps().size() - 1);
                        previousEndMeters = segment.cumulativeEndMeters();
                }

                var lastSegment = route.getRouteSegments().get(route.getRouteSegments().size() - 1);
                assertEquals(route.getRouteSteps().size() - 2, lastSegment.stepIndex());

                double previousRangeEnd = 0.0;
                for (int index = 0; index < route.getStepProgressRanges().size(); index++) {
                        var range = route.getStepProgressRanges().get(index);
                        assertEquals(index, range.stepIndex());
                        assertTrue(range.endMeters() >= range.startMeters());
                        assertEquals(previousRangeEnd, range.startMeters(), 0.01);
                        previousRangeEnd = range.endMeters();
                }

                assertEquals(0.0, route.getStepProgressRanges().get(0).startMeters(), 0.0001);
                assertEquals(route.getTotalDistanceMeters(), route.getStepProgressRanges().get(route.getStepProgressRanges().size() - 1).endMeters(), 0.0001);

                assertEquals(route.getTotalDistanceMeters(), previousEndMeters, 0.01);
                assertEquals(route.getTotalDistanceMeters(), previousRangeEnd, 0.01);
        }
}
