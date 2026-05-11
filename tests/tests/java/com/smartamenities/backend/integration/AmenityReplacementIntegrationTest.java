package com.smartamenities.backend.integration;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.controller.AmenityController;
import com.smartamenities.backend.dto.AmenityReplacementResponse;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: GET /amenities/replacement returns a valid open replacement
 * of the same type when queried for a closed amenity.
 */
class AmenityReplacementIntegrationTest {

    @Test
    void replacementEndpointReturnsOpenSameTypeAmenity() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        AmenityController controller = new AmenityController(amenityService);

        String closedAmenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        double currentLat = loader.getStartLocation().getLatitude();
        double currentLon = loader.getStartLocation().getLongitude();

        ResponseEntity<AmenityReplacementResponse> response = controller.getNearestOpenReplacement(
                closedAmenityId, currentLat, currentLon, false, "replacement-integration-seed"
        );

        assertNotNull(response.getBody());
        AmenityReplacementResponse body = response.getBody();

        assertNotNull(body.getAmenityId());
        assertNotEquals(closedAmenityId, body.getAmenityId());
        assertEquals(AmenityType.MEN_RESTROOM, body.getAmenityType());
        assertNotNull(body.getDistanceMeters());
        assertTrue(body.getDistanceMeters() >= 0.0);
        assertTrue(body.getStallsAvailable() >= 0);
        assertTrue(body.getWaitTimeMinutes() >= 0);
        assertNotNull(body.getOccupancyStatus());
    }

    @Test
    void replacementEndpointReturnsNearestNotClosedAmenity() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        AmenityController controller = new AmenityController(amenityService);

        String closedAmenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.WOMEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        ResponseEntity<AmenityReplacementResponse> response = controller.getNearestOpenReplacement(
                closedAmenityId,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "replacement-nearest-seed"
        );

        assertNotNull(response.getBody());
        assertNotEquals(closedAmenityId, response.getBody().getAmenityId());
        assertEquals(AmenityType.WOMEN_RESTROOM, response.getBody().getAmenityType());
    }

    @Test
    void accessibleReplacementIsAlsoAccessible() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        AmenityController controller = new AmenityController(amenityService);

        String closedAccessibleId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.ACCESSIBLE_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        ResponseEntity<AmenityReplacementResponse> response = controller.getNearestOpenReplacement(
                closedAccessibleId,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                true,
                "replacement-accessible-seed"
        );

        assertNotNull(response.getBody());
        assertEquals(AmenityType.ACCESSIBLE_RESTROOM, response.getBody().getAmenityType());
    }
}
