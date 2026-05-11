package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Passenger deviates from the original route; a new route is computed from
 * the deviation position to the same destination.
 * Covers UC: Dynamic Rerouting.
 */
class DynamicReroutingScenarioTest {

    // A point on the Level 3 walkable network, distinct from the default start.
    private static final double DEVIATION_LAT = 32.89950;
    private static final double DEVIATION_LON = -97.04496;

    @Test
    void rerouteStartsAtDeviationCoordinates() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        Amenity destination = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.ACCESSIBLE_RESTROOM)
                .findFirst()
                .orElseThrow();

        Route reroutedRoute = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                DEVIATION_LAT + "," + DEVIATION_LON,
                false,
                destination.getId(),
                DEVIATION_LAT,
                DEVIATION_LON
        ));

        assertNotNull(reroutedRoute);
        assertEquals(DEVIATION_LAT, reroutedRoute.getStartLatitude(), 0.000001);
        assertEquals(DEVIATION_LON, reroutedRoute.getStartLongitude(), 0.000001);
    }

    @Test
    void reroutedRouteReachesSameDestination() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        Amenity destination = loader.getAmenities().get(0);

        Route originalRoute = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                destination.getId(),
                null,
                null
        ));

        Route reroutedRoute = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                DEVIATION_LAT + "," + DEVIATION_LON,
                false,
                destination.getId(),
                DEVIATION_LAT,
                DEVIATION_LON
        ));

        assertNotNull(reroutedRoute);
        assertEquals(originalRoute.getDestination(), reroutedRoute.getDestination());
        assertEquals(originalRoute.getDestinationLatitude(), reroutedRoute.getDestinationLatitude(), 0.000001);
        assertEquals(originalRoute.getDestinationLongitude(), reroutedRoute.getDestinationLongitude(), 0.000001);
    }

    @Test
    void reroutedRouteHasValidStepsAndDistance() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        Amenity destination = loader.getAmenities().get(0);

        Route reroutedRoute = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                DEVIATION_LAT + "," + DEVIATION_LON,
                true,
                destination.getId(),
                DEVIATION_LAT,
                DEVIATION_LON
        ));

        assertNotNull(reroutedRoute.getRouteGeoPoints());
        assertFalse(reroutedRoute.getRouteGeoPoints().isEmpty());
        assertNotNull(reroutedRoute.getRouteSteps());
        assertFalse(reroutedRoute.getRouteSteps().isEmpty());
        assertTrue(reroutedRoute.getTotalDistanceMeters() > 0.0);

        String lastStep = reroutedRoute.getRouteSteps()
                .get(reroutedRoute.getRouteSteps().size() - 1).toLowerCase();
        assertTrue(lastStep.startsWith("arrive at"), "Last step should be arrival: " + lastStep);
    }
}
