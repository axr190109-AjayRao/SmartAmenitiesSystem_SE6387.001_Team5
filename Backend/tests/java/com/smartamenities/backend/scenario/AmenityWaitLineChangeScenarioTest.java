package com.smartamenities.backend.scenario;

import com.smartamenities.backend.TestServiceFactory;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.OccupancyStatus;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.RouteMetricsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario: Wait time display updates during navigation, demonstrating live data changes.
 * Covers UC: Amenity Wait Line Change.
 */
class AmenityWaitLineChangeScenarioTest {

    @Test
    void waitTimesVaryAcrossDifferentAmenities() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "wait-line-diversity-seed"
        );

        Set<Integer> waitTimes = results.stream()
                .filter(ad -> ad.liveStatus().isSelectable())
                .map(ad -> ad.liveStatus().waitTimeMinutes())
                .collect(Collectors.toSet());

        assertTrue(waitTimes.size() > 1,
                "Different amenities should have different wait times; got: " + waitTimes);
    }

    @Test
    void waitTimesChangeAcrossPollingTicks() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        AtomicInteger callCounter = new AtomicInteger(0);
        AmenityOccupancyProvider changingProvider = (amenity, lat, lon, seed) -> {
            int n = callCounter.incrementAndGet();
            int wait = (n * 3) % 15;
            return new AmenityLiveStatus(AmenityStatus.OPEN, null, wait, 5 - (n % 4), OccupancyStatus.LOW);
        };

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, changingProvider);

        String amenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.MEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();
        String sessionSeed = "wait-line-change-" + System.nanoTime();

        List<Integer> openWaitTimes = new ArrayList<>();
        for (int poll = 1; poll <= 20; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.OPEN) {
                openWaitTimes.add(status.waitTimeMinutes());
            }
        }

        assertFalse(openWaitTimes.isEmpty(), "Should have at least one open poll");

        // Values from the incrementing mock should not all be identical.
        long distinctCount = openWaitTimes.stream().distinct().count();
        assertTrue(distinctCount > 1,
                "Wait times should change across polls; got: " + openWaitTimes);
    }

    @Test
    void waitTimesAreWithinReasonableBounds() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, "wait-bounds-seed"
        );

        for (AmenityService.AmenityDistance ad : results) {
            int wait = ad.liveStatus().waitTimeMinutes();
            assertTrue(wait >= 0, "Wait time must be non-negative");
            assertTrue(wait <= 20, "Wait time should not exceed simulated maximum of 20 minutes");
        }
    }
}
