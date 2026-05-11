package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.RouteGeoPoint;
import com.smartamenities.backend.dto.RouteProgressRequest;
import com.smartamenities.backend.dto.RouteProgressResponse;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.dto.RouteResponse;
import com.smartamenities.backend.dto.RouteStepProgressRangeResponse;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteProgressService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteControllerTest {

    @Test
    void createRouteReturnsProgressMetadata() {
        RouteController controller = buildController();
        Amenity destination = buildLoader().getAmenities().get(0);

        RouteResponse response = controller.createRoute(
            null,
            new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null)
        ).getBody();

        assertNotNull(response);
        assertNotNull(response.getRouteGeoPoints());
        assertNotNull(response.getRouteSegments());
        assertNotNull(response.getStepProgressRanges());
        assertNotNull(response.getTotalDistanceMeters());
        assertFalse(response.getRouteGeoPoints().isEmpty());
        assertFalse(response.getRouteSegments().isEmpty());
        assertFalse(response.getStepProgressRanges().isEmpty());
        assertTrue(response.getTotalDistanceMeters() > 0.0);

        double previousSegmentEnd = 0.0;
        for (int index = 0; index < response.getRouteSegments().size(); index++) {
            var segment = response.getRouteSegments().get(index);
            assertEquals(index, segment.getSegmentIndex());
            assertTrue(segment.getCumulativeStartMeters() >= previousSegmentEnd - 0.01);
            assertTrue(segment.getCumulativeEndMeters() >= segment.getCumulativeStartMeters());
            assertTrue(segment.getStepIndex() >= 0);
            previousSegmentEnd = segment.getCumulativeEndMeters();
        }

        double previousRangeEnd = 0.0;
        for (int index = 0; index < response.getStepProgressRanges().size(); index++) {
            var range = response.getStepProgressRanges().get(index);
            assertEquals(index, range.getStepIndex());
            assertTrue(range.getEndMeters() >= range.getStartMeters());
            assertTrue(range.getStartMeters() >= previousRangeEnd - 0.01);
            previousRangeEnd = range.getEndMeters();
        }

        assertEquals(response.getTotalDistanceMeters(), previousSegmentEnd, 0.01);
        assertEquals(response.getTotalDistanceMeters(), previousRangeEnd, 0.01);
    }

    @Test
    void simulateProgressSnapsStartMidBoundaryAndEnd() {
        RouteController controller = buildController();
        Amenity destination = buildLoader().getAmenities().get(0);

        RouteResponse routeResponse = controller.createRoute(
            null,
            new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null)
        ).getBody();

        assertNotNull(routeResponse);
        assertNotNull(routeResponse.getRouteGeoPoints());
        assertNotNull(routeResponse.getStepProgressRanges());
        assertTrue(routeResponse.getStepProgressRanges().size() >= 2);

        double firstBoundaryMeters = routeResponse.getStepProgressRanges().get(0).getEndMeters();
        double totalDistanceMeters = routeResponse.getTotalDistanceMeters();
        List<RouteGeoPoint> routeGeoPoints = routeResponse.getRouteGeoPoints();
        List<RouteStepProgressRangeResponse> stepProgressRanges = routeResponse.getStepProgressRanges();

        RouteProgressResponse start = controller.simulateProgress(new RouteProgressRequest(
                routeGeoPoints,
                stepProgressRanges,
                0.0,
                totalDistanceMeters,
                null,
                null
        )).getBody();
        RouteProgressResponse mid = controller.simulateProgress(new RouteProgressRequest(
                routeGeoPoints,
                stepProgressRanges,
                Math.max(0.1, firstBoundaryMeters / 2.0),
                totalDistanceMeters,
                null,
                null
        )).getBody();
        RouteProgressResponse boundary = controller.simulateProgress(new RouteProgressRequest(
                routeGeoPoints,
                stepProgressRanges,
                firstBoundaryMeters,
                totalDistanceMeters,
                null,
                null
        )).getBody();
        RouteProgressResponse beyond = controller.simulateProgress(new RouteProgressRequest(
                routeGeoPoints,
                stepProgressRanges,
                totalDistanceMeters + 250.0,
                totalDistanceMeters,
                null,
                null
        )).getBody();
        RouteProgressResponse atTotal = controller.simulateProgress(new RouteProgressRequest(
            routeGeoPoints,
            stepProgressRanges,
            totalDistanceMeters,
            totalDistanceMeters,
            null,
            null
        )).getBody();
        RouteProgressResponse nearEnd = controller.simulateProgress(new RouteProgressRequest(
            routeGeoPoints,
            stepProgressRanges,
            Math.max(0.0, totalDistanceMeters - 0.25),
            totalDistanceMeters,
            null,
            null
        )).getBody();

        assertNotNull(start);
        assertNotNull(mid);
        assertNotNull(boundary);
        assertNotNull(beyond);
        assertNotNull(atTotal);
        assertNotNull(nearEnd);

        assertEquals(0, start.getActiveStepIndex());
        assertEquals(0, mid.getActiveStepIndex());
        assertTrue(mid.getRemainingDistanceMeters() < start.getRemainingDistanceMeters());
        assertEquals(1, boundary.getActiveStepIndex());
        assertEquals(stepProgressRanges.size() - 1, atTotal.getActiveStepIndex());
        assertEquals(stepProgressRanges.size() - 1, beyond.getActiveStepIndex());
        assertEquals(stepProgressRanges.size() - 1, nearEnd.getActiveStepIndex());
        assertEquals(totalDistanceMeters, atTotal.getClampedProgressMeters(), 0.01);
        assertEquals(totalDistanceMeters, beyond.getClampedProgressMeters(), 0.01);
        assertEquals(routeGeoPoints.get(0).getLatitude(), start.getSnappedLatitude(), 0.000001);
        assertEquals(routeGeoPoints.get(0).getLongitude(), start.getSnappedLongitude(), 0.000001);
        assertEquals(routeGeoPoints.get(routeGeoPoints.size() - 1).getLatitude(), beyond.getSnappedLatitude(), 0.000001);
        assertEquals(routeGeoPoints.get(routeGeoPoints.size() - 1).getLongitude(), beyond.getSnappedLongitude(), 0.000001);
        assertFalse(boundary.getActiveInstruction().isBlank());
        assertFalse(beyond.getRemainingSteps().isEmpty());
        assertFalse(start.isArrived());
        assertTrue(atTotal.isArrived());
        assertTrue(nearEnd.isArrived());
        assertTrue(beyond.isArrived());
        assertEquals(0.0, atTotal.getRemainingDistanceMeters(), 0.0);
        assertEquals(0.0, nearEnd.getRemainingDistanceMeters(), 0.0);
        assertEquals(0.0, beyond.getRemainingDistanceMeters(), 0.0);
    }

        @Test
        void simulateProgressDoesNotPrematurelyCompleteAtThreeMetersButCompletesAtEnd() {
        RouteController controller = buildController();
        Amenity destination = buildLoader().getAmenities().get(0);

        RouteResponse routeResponse = controller.createRoute(
            null,
            new RouteRequest(destination.getDisplayName(), "Terminal D Level 3 starting point", true, destination.getId(), null, null)
        ).getBody();

        assertNotNull(routeResponse);
        double totalDistanceMeters = routeResponse.getTotalDistanceMeters();

        RouteProgressResponse aroundThreeMeters = controller.simulateProgress(new RouteProgressRequest(
            routeResponse.getRouteGeoPoints(),
            routeResponse.getStepProgressRanges(),
            Math.max(0.0, totalDistanceMeters - 3.0),
            totalDistanceMeters,
            null,
            null
        )).getBody();

        RouteProgressResponse atEnd = controller.simulateProgress(new RouteProgressRequest(
            routeResponse.getRouteGeoPoints(),
            routeResponse.getStepProgressRanges(),
            totalDistanceMeters,
            totalDistanceMeters,
            null,
            null
        )).getBody();

        assertNotNull(aroundThreeMeters);
        assertNotNull(atEnd);
        assertFalse(aroundThreeMeters.isArrived());
        assertTrue(aroundThreeMeters.getRemainingDistanceMeters() > 0.0);
        assertTrue(atEnd.isArrived());
        assertEquals(routeResponse.getStepProgressRanges().size() - 1, atEnd.getActiveStepIndex());
        assertEquals(0.0, atEnd.getRemainingDistanceMeters(), 0.0);
        }

    private static RouteController buildController() {
        GeoJsonAmenityLoader loader = buildLoader();
        RouteMetricsService routeMetricsService = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, routeMetricsService);
        RouteService routeService = new RouteService(amenityService, loader, routeMetricsService);
        RouteProgressService routeProgressService = new RouteProgressService();
        return new RouteController(routeService, routeProgressService);
    }

    private static GeoJsonAmenityLoader buildLoader() {
        return new GeoJsonAmenityLoader();
    }
}
