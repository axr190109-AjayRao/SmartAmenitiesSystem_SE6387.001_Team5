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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Non-disabled passenger navigates from current position to the nearest men's restroom.
 * Covers UC: Navigate A→B (non-disabled).
 */
class NavigateToAmenityScenarioTest {

    @Test
    void passengerCanNavigateToNearestMensRestroom() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        double currentLat = loader.getStartLocation().getLatitude();
        double currentLon = loader.getStartLocation().getLongitude();

        List<AmenityService.AmenityDistance> amenities = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.MEN_RESTROOM, currentLat, currentLon, false, "nav-scenario-seed"
        );

        AmenityService.AmenityDistance nearest = amenities.stream()
                .filter(ad -> ad.liveStatus().isSelectable())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No selectable men's restroom available"));

        Route route = routeService.generateRoute(new RouteRequest(
                nearest.amenity().getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                nearest.amenity().getId(),
                null,
                null
        ));

        assertNotNull(route);
        assertNotNull(route.getRouteGeoPoints());
        assertFalse(route.getRouteGeoPoints().isEmpty());
        assertNotNull(route.getRouteSteps());
        assertFalse(route.getRouteSteps().isEmpty());
        assertNotNull(route.getTotalDistanceMeters());
        assertTrue(route.getTotalDistanceMeters() > 0.0);
        assertNotNull(route.getEstimatedTime());
        assertTrue(route.getEstimatedTime().endsWith(" min"));

        String lastStep = route.getRouteSteps().get(route.getRouteSteps().size() - 1).toLowerCase();
        assertTrue(lastStep.startsWith("arrive at"), "Last step should be arrival, got: " + lastStep);
    }

    @Test
    void routeDestinationMatchesSelectedAmenity() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        Amenity destination = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.WOMEN_RESTROOM)
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

        assertEquals(destination.getDisplayName(), route.getDestination());
        assertEquals(destination.getLatitude(), route.getDestinationLatitude(), 0.000001);
        assertEquals(destination.getLongitude(), route.getDestinationLongitude(), 0.000001);
    }
}
