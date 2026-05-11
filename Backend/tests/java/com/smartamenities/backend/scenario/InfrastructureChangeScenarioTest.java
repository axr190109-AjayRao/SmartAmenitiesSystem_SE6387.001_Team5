package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.dto.InfrastructureRouteStatusRequest;
import com.smartamenities.backend.dto.InfrastructureRouteStatusResponse;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.integration.adminworkstation.SimulatedAdminWorkstationAdapter;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.InfrastructureStatusService;
import com.smartamenities.backend.service.RouteMetricsService;
import com.smartamenities.backend.service.RouteService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: A corridor blockage is detected on the passenger's active route during navigation.
 * Covers UC: Infrastructure Change.
 */
class InfrastructureChangeScenarioTest {

    @Test
    void corridorBlockageIsDetectedOnActiveRoute() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);
        RouteService routeService = new RouteService(amenityService, loader, rms);
        InfrastructureStatusService infraService = new InfrastructureStatusService(
                new SimulatedAdminWorkstationAdapter()
        );

        Amenity destination = loader.getAmenities().get(0);
        Route route = routeService.generateRoute(new RouteRequest(
                destination.getDisplayName(),
                "Terminal D Level 3 starting point",
                false,
                destination.getId(),
                null,
                null
        ));

        // Build segment ID list from route segment indices.
        List<String> segmentIds = IntStream.range(0, route.getRouteSegments().size())
                .mapToObj(i -> "seg-" + i)
                .toList();

        InfrastructureRouteStatusResponse response = infraService.checkRouteStatus(
                new InfrastructureRouteStatusRequest(segmentIds, null),
                "infra-scenario-seed"
        );

        assertTrue(response.isCorridorBlocked(), "Simulated adapter should report a blocked corridor");
        assertNotNull(response.getBlockedSegmentId());
        assertNotNull(response.getReason());
        assertNotNull(response.getAlertId());
        assertTrue(response.getAlertId().startsWith("INFRA-"));
        assertNotNull(response.getEstimatedClearanceMinutes());
        assertTrue(response.getEstimatedClearanceMinutes() > 0);
    }

    @Test
    void infrastructureResponseIncludesHumanReadableDescription() {
        InfrastructureStatusService infraService = new InfrastructureStatusService(
                new SimulatedAdminWorkstationAdapter()
        );

        InfrastructureRouteStatusResponse response = infraService.checkRouteStatus(
                new InfrastructureRouteStatusRequest(List.of("corridor-A", "corridor-B", "corridor-C"), null),
                "infra-description-seed"
        );

        assertTrue(response.isCorridorBlocked());
        assertNotNull(response.getBlockedSegmentDescription());
        assertFalse(response.getBlockedSegmentDescription().isBlank());
        assertNotNull(response.getReason());
        assertFalse(response.getReason().isBlank());
    }

    @Test
    void infrastructureBlockedSegmentIsOneOfTheProvidedSegments() {
        InfrastructureStatusService infraService = new InfrastructureStatusService(
                new SimulatedAdminWorkstationAdapter()
        );

        List<String> segmentIds = List.of("segment-alpha", "segment-beta", "segment-gamma");
        InfrastructureRouteStatusResponse response = infraService.checkRouteStatus(
                new InfrastructureRouteStatusRequest(segmentIds, null),
                "infra-segment-seed"
        );

        assertTrue(response.isCorridorBlocked());
        assertTrue(segmentIds.contains(response.getBlockedSegmentId()),
                "Blocked segment ID must be one of the provided segment IDs");
    }
}
