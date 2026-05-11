package com.smartamenities.backend.integration;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.controller.RouteController;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.dto.RouteResponse;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteProgressService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: Reroute request correctly uses the passenger's current deviation
 * coordinates as the new route origin rather than the default terminal start.
 */
class RerouteOriginIntegrationTest {

    private static final double DEVIATION_LAT = 32.89960;
    private static final double DEVIATION_LON = -97.04490;

    @Test
    void routeControllerUsesDeviationCoordsAsOrigin() {
        RouteController controller = buildController();
        Amenity destination = buildLoader().getAmenities().get(0);

        ResponseEntity<RouteResponse> response = controller.createRoute(
                null,
                new RouteRequest(
                        destination.getDisplayName(),
                        DEVIATION_LAT + "," + DEVIATION_LON,
                        false,
                        destination.getId(),
                        DEVIATION_LAT,
                        DEVIATION_LON
                )
        );

        assertNotNull(response.getBody());
        RouteResponse body = response.getBody();

        assertEquals(DEVIATION_LAT, body.getStartLatitude(), 0.000001);
        assertEquals(DEVIATION_LON, body.getStartLongitude(), 0.000001);
        assertNotNull(body.getRouteGeoPoints());
        assertFalse(body.getRouteGeoPoints().isEmpty());
        assertEquals(DEVIATION_LAT, body.getRouteGeoPoints().get(0).getLatitude(), 0.000001);
        assertEquals(DEVIATION_LON, body.getRouteGeoPoints().get(0).getLongitude(), 0.000001);
    }

    @Test
    void rerouteReachesSameDestinationAsOriginalRoute() {
        RouteController controller = buildController();
        GeoJsonAmenityLoader loader = buildLoader();
        Amenity destination = loader.getAmenities().get(0);

        ResponseEntity<RouteResponse> originalResponse = controller.createRoute(
                null,
                new RouteRequest(
                        destination.getDisplayName(),
                        "Terminal D Level 3 starting point",
                        false,
                        destination.getId(),
                        null,
                        null
                )
        );

        ResponseEntity<RouteResponse> rerouteResponse = controller.createRoute(
                null,
                new RouteRequest(
                        destination.getDisplayName(),
                        DEVIATION_LAT + "," + DEVIATION_LON,
                        false,
                        destination.getId(),
                        DEVIATION_LAT,
                        DEVIATION_LON
                )
        );

        assertNotNull(originalResponse.getBody());
        assertNotNull(rerouteResponse.getBody());

        assertEquals(
                originalResponse.getBody().getDestinationLatitude(),
                rerouteResponse.getBody().getDestinationLatitude(),
                0.000001
        );
        assertEquals(
                originalResponse.getBody().getDestinationLongitude(),
                rerouteResponse.getBody().getDestinationLongitude(),
                0.000001
        );
    }

    @Test
    void rerouteHasNonZeroDistanceAndValidSteps() {
        RouteController controller = buildController();
        Amenity destination = buildLoader().getAmenities().get(0);

        ResponseEntity<RouteResponse> response = controller.createRoute(
                null,
                new RouteRequest(
                        destination.getDisplayName(),
                        DEVIATION_LAT + "," + DEVIATION_LON,
                        false,
                        destination.getId(),
                        DEVIATION_LAT,
                        DEVIATION_LON
                )
        );

        assertNotNull(response.getBody());
        RouteResponse body = response.getBody();
        assertTrue(body.getTotalDistanceMeters() > 0.0);
        assertFalse(body.getRouteSteps().isEmpty());
        assertTrue(body.getRouteSteps().get(body.getRouteSteps().size() - 1)
                .getInstruction().toLowerCase().startsWith("arrive at"));
    }

    private static RouteController buildController() {
        GeoJsonAmenityLoader loader = buildLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);
        return new RouteController(routeService, new RouteProgressService());
    }

    private static GeoJsonAmenityLoader buildLoader() {
        return new GeoJsonAmenityLoader();
    }
}
