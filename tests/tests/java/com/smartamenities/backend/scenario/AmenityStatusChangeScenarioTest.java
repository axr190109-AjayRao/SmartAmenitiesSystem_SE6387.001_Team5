package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: An open amenity changes to CLOSED during navigation, triggering the reroute popup.
 * Covers UC: Amenity Status Change.
 */
class AmenityStatusChangeScenarioTest {

    private static final int POLL_COUNT = 20;

    @Test
    void amenityTransitionsFromOpenToClosedExactlyOnce() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String amenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();

        String sessionSeed = "status-change-scenario-" + System.nanoTime();
        int closedCount = 0;

        for (int poll = 1; poll <= POLL_COUNT; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.CLOSED) {
                closedCount++;
            }
        }

        assertEquals(1, closedCount,
                "Amenity should transition to CLOSED exactly once per session");
    }

    @Test
    void closureOccursWithinExpectedNavigationWindow() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String amenityId = loader.getAmenities().get(0).getId();
        String sessionSeed = "closure-window-scenario-" + System.nanoTime();
        int firstClosedPoll = -1;

        for (int poll = 1; poll <= POLL_COUNT; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.CLOSED && firstClosedPoll < 0) {
                firstClosedPoll = poll;
            }
        }

        assertNotEquals(-1, firstClosedPoll, "Closure should occur within 20 polls");
        assertTrue(firstClosedPoll >= 1 && firstClosedPoll <= 12,
                "Closure should occur within polls 1-12 (early/mid/late window), got poll " + firstClosedPoll);
    }

    @Test
    void closureStatusHasNonNullReason() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        String amenityId = loader.getAmenities().get(0).getId();
        String sessionSeed = "closure-reason-scenario-" + System.nanoTime();

        AmenityService.LiveStatus closedStatus = null;
        for (int poll = 1; poll <= POLL_COUNT; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.CLOSED) {
                closedStatus = status;
                break;
            }
        }

        assertNotNull(closedStatus, "Should find a CLOSED status within 20 polls");
        assertNotNull(closedStatus.statusReason(), "Closed status must include a reason");
        assertEquals(0, closedStatus.stallsAvailable());
        assertEquals(0, closedStatus.waitTimeMinutes());
    }
}
