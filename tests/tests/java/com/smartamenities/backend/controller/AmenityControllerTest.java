package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.AmenityResponse;
import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmenityControllerTest {

    @Test
    void getAmenitiesReturnsRouteSortedDistances() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
        AmenityController controller = new AmenityController(amenityService);

        double startLat = loader.getStartLocation().getLatitude();
        double startLon = loader.getStartLocation().getLongitude();

        ResponseEntity<List<AmenityResponse>> response = controller.getAmenities(
                "WOMEN_RESTROOM",
                startLat,
            startLon,
            null
        );

        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
        assertNotNull(response.getBody().get(0).getDistanceMeters());

        if (response.getBody().size() > 1) {
            Double first = response.getBody().get(0).getDistanceMeters();
            Double second = response.getBody().get(1).getDistanceMeters();
            assertNotNull(first);
            assertNotNull(second);
            assertTrue(first <= second);
        }
    }

    @Test
    void getAmenitiesWithoutCoordinatesUsesDefaultStartAnchor() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
        AmenityController controller = new AmenityController(amenityService);

        ResponseEntity<List<AmenityResponse>> noCoords = controller.getAmenities("MEN_RESTROOM", null, null, null);
        ResponseEntity<List<AmenityResponse>> explicitStart = controller.getAmenities(
                "MEN_RESTROOM",
                loader.getStartLocation().getLatitude(),
            loader.getStartLocation().getLongitude(),
            null
        );

        assertNotNull(noCoords.getBody());
        assertNotNull(explicitStart.getBody());
        assertFalse(noCoords.getBody().isEmpty());
        assertFalse(explicitStart.getBody().isEmpty());

        AmenityResponse firstNoCoords = noCoords.getBody().get(0);
        AmenityResponse firstExplicit = explicitStart.getBody().get(0);

        assertEquals(firstExplicit.getId(), firstNoCoords.getId());
        assertNotNull(firstNoCoords.getDistanceMeters());
        assertNotNull(firstExplicit.getDistanceMeters());
        assertEquals(firstExplicit.getDistanceMeters(), firstNoCoords.getDistanceMeters(), 1.0);
    }
}
