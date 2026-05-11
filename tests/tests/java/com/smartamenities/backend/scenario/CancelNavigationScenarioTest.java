package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.RouteGeoPoint;
import com.smartamenities.backend.dto.RouteProgressRequest;
import com.smartamenities.backend.dto.RouteProgressResponse;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.dto.RouteStepProgressRangeResponse;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteProgressService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Passenger initiates navigation to an amenity but cancels before arriving.
 * Covers UC: Cancel Navigation (<<Extends>> Navigate to Amenity).
 */
class CancelNavigationScenarioTest {

    @Test
    void passengerIsOnRouteBeforeCanceling() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);
        RouteProgressService progressService = new RouteProgressService();

        Amenity destination = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow();

        Route route = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                destination.getId(),
                null,
                null
        ));

        assertNotNull(route);
        assertFalse(route.getRouteGeoPoints().isEmpty());
        assertFalse(route.getStepProgressRanges().isEmpty());

        List<RouteGeoPoint> geoPoints = route.getRouteGeoPoints().stream()
                .map(p -> new RouteGeoPoint(p.getLatitude(), p.getLongitude()))
                .toList();

        List<RouteStepProgressRangeResponse> stepRanges = route.getStepProgressRanges().stream()
                .map(r -> new RouteStepProgressRangeResponse(
                        r.stepIndex(), r.startMeters(), r.endMeters(), r.instruction()))
                .toList();

        // Passenger is mid-route at the start position — simulate progress check before cancel
        RouteGeoPoint startPoint = geoPoints.get(0);
        RouteProgressResponse progress = progressService.simulateProgress(new RouteProgressRequest(
                geoPoints,
                stepRanges,
                0.0,
                route.getTotalDistanceMeters(),
                startPoint.getLatitude(),
                startPoint.getLongitude()
        ));

        // Passenger was actively navigating (on-route) at the moment of cancellation
        assertFalse(progress.isOffRoute(),
                "Passenger should be on-route mid-navigation at the point of cancellation");
    }

    @Test
    void newNavigationSucceedsAfterCanceledSession() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        // Original navigation session — passenger starts navigating to a men's restroom
        Amenity originalDestination = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow();

        Route canceledRoute = routeService.generateRoute(new RouteRequest(
                originalDestination.getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                originalDestination.getId(),
                null,
                null
        ));

        assertNotNull(canceledRoute);

        // Passenger cancels — new navigation session starts to a different amenity type
        Amenity newDestination = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.WOMEN_RESTROOM)
                .findFirst()
                .orElseThrow();

        Route newRoute = routeService.generateRoute(new RouteRequest(
                newDestination.getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                newDestination.getId(),
                null,
                null
        ));

        // New route is valid and independent of the canceled session
        assertNotNull(newRoute);
        assertFalse(newRoute.getRouteGeoPoints().isEmpty());
        assertFalse(newRoute.getRouteSteps().isEmpty());
        assertTrue(newRoute.getTotalDistanceMeters() > 0.0);
        assertNotEquals(canceledRoute.getDestination(), newRoute.getDestination(),
                "New route destination should differ from the canceled route");
        assertTrue(newRoute.getRouteSteps().get(newRoute.getRouteSteps().size() - 1)
                .toLowerCase().startsWith("arrive at"));
    }
}
