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
 * Scenario: Passenger with mobility needs navigates to the nearest accessible restroom.
 * Covers UC: Navigate A→B (disabled user, accessibilityOn=true).
 */
class AccessibleRouteScenarioTest {

    @Test
    void accessibleRouteIsGeneratedToAccessibleRestroom() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        List<AmenityService.AmenityDistance> amenities = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.ACCESSIBLE_RESTROOM,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                true,
                "accessible-scenario-seed"
        );

        AmenityService.AmenityDistance nearest = amenities.stream()
                .filter(ad -> ad.liveStatus().isSelectable())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No selectable accessible restroom"));

        Route route = routeService.generateRoute(new RouteRequest(
                nearest.amenity().getDisplayName(),
                "Terminal D Level 3 starting point",
                true,
                nearest.amenity().getId(),
                null,
                null
        ));

        assertNotNull(route);
        assertTrue(route.isAccessibilityOn());
        assertNotNull(route.getRouteGeoPoints());
        assertFalse(route.getRouteGeoPoints().isEmpty());
        assertTrue(route.getTotalDistanceMeters() > 0.0);
        assertFalse(route.getRouteSteps().isEmpty());
        assertTrue(route.getRouteSteps().get(route.getRouteSteps().size() - 1)
                .toLowerCase().startsWith("arrive at"));
    }

    @Test
    void accessibleDistanceIsAtLeastAsLongAsStandardDistance() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        Amenity target = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.ACCESSIBLE_RESTROOM)
                .findFirst()
                .orElseThrow();

        double standardDistance = rms.computeRouteMetrics(target, false, null, null)
                .pathResult().totalDistanceMeters();
        double accessibleDistance = rms.computeRouteMetrics(target, true, null, null)
                .pathResult().totalDistanceMeters();

        assertTrue(accessibleDistance >= standardDistance - 0.001,
                "Accessible route should be same length or longer than standard route");
    }

    @Test
    void accessibleAmenityListContainsOnlyAccessibleRestrooms() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                AmenityType.ACCESSIBLE_RESTROOM, null, null, true, "accessible-filter-seed"
        );

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(
                ad -> ad.amenity().getAmenityType() == AmenityType.ACCESSIBLE_RESTROOM
        ));
    }
}
