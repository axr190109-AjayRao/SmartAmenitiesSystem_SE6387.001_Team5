package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Amenity closes mid-navigation → reroute popup appears → passenger accepts
 * replacement → new route is generated to the replacement amenity.
 * Covers UC: Amenity Status Change reroute flow.
 */
class DismissRerouteScenarioTest {

    private static final int MAX_POLLS = 20;

    @Test
    void replacementRouteIsGeneratedAfterAmenityClosure() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);

        String sessionSeed = "dismiss-reroute-scenario-" + System.nanoTime();
        double currentLat = loader.getStartLocation().getLatitude();
        double currentLon = loader.getStartLocation().getLongitude();

        // Step 1: pick an amenity to navigate to.
        String originalAmenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        // Step 2: poll until closure triggers.
        boolean closureDetected = false;
        for (int poll = 1; poll <= MAX_POLLS; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    originalAmenityId, currentLat, currentLon, sessionSeed
            );
            if (status.status() == AmenityStatus.CLOSED) {
                closureDetected = true;
                break;
            }
        }

        assertTrue(closureDetected, "Closure should have been detected within " + MAX_POLLS + " polls");

        // Step 3: find nearest open replacement (reroute popup accepted).
        AmenityService.AmenityRecommendation replacement = amenityService.findNearestOpenAmenitySameType(
                originalAmenityId, currentLat, currentLon, false, sessionSeed
        );

        assertNotNull(replacement);
        assertNotEquals(originalAmenityId, replacement.amenity().getId());
        assertEquals(AmenityType.MEN_RESTROOM, replacement.amenity().getAmenityType());
        assertTrue(replacement.liveStatus().isSelectable());

        // Step 4: generate route to replacement (passenger accepted the reroute).
        Route replacementRoute = routeService.generateRoute(new RouteRequest(
                replacement.amenity().getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                replacement.amenity().getId(),
                null,
                null
        ));

        assertNotNull(replacementRoute);
        assertFalse(replacementRoute.getRouteGeoPoints().isEmpty());
        assertTrue(replacementRoute.getTotalDistanceMeters() > 0.0);
        assertTrue(replacementRoute.getRouteSteps().get(replacementRoute.getRouteSteps().size() - 1)
                .toLowerCase().startsWith("arrive at"));
    }

    @Test
    void replacementAmenityIsOpenAndSameType() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String closedAmenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.WOMEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        AmenityService.AmenityRecommendation replacement = amenityService.findNearestOpenAmenitySameType(
                closedAmenityId,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "replacement-type-seed"
        );

        assertEquals(AmenityType.WOMEN_RESTROOM, replacement.amenity().getAmenityType());
        assertEquals(AmenityStatus.OPEN, replacement.liveStatus().status());
        assertNotEquals(closedAmenityId, replacement.amenity().getId());
    }
}
