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
 * Scenario: Occupancy display updates during navigation with meaningful status diversity.
 * Covers UC: Occupancy Change.
 */
class AmenityOccupancyChangeScenarioTest {

    @Test
    void occupancyStatusVariesAcrossDifferentAmenities() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null,
                loader.getStartLocation().getLatitude(),
                loader.getStartLocation().getLongitude(),
                false,
                "occupancy-diversity-seed"
        );

        Set<OccupancyStatus> statuses = results.stream()
                .filter(ad -> ad.liveStatus().isSelectable())
                .map(ad -> ad.liveStatus().occupancyStatus())
                .collect(Collectors.toSet());

        assertTrue(statuses.size() > 1,
                "Amenity list should contain multiple occupancy statuses; got: " + statuses);
    }

    @Test
    void occupancyStatusChangesAcrossPollingTicks() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);

        OccupancyStatus[] cycle = {
            OccupancyStatus.LOW, OccupancyStatus.MEDIUM, OccupancyStatus.HIGH, OccupancyStatus.MEDIUM
        };
        AtomicInteger callCounter = new AtomicInteger(0);
        AmenityOccupancyProvider changingProvider = (amenity, lat, lon, seed) -> {
            int n = callCounter.getAndIncrement();
            OccupancyStatus status = cycle[n % cycle.length];
            int stalls = switch (status) {
                case LOW -> 8;
                case MEDIUM -> 3;
                case HIGH -> 1;
                case FULL -> 0;
            };
            return new AmenityLiveStatus(AmenityStatus.OPEN, null, 1, stalls, status);
        };

        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms, changingProvider);

        String amenityId = loader.getAmenities().stream()
                .filter(a -> a.getAmenityType() == AmenityType.WOMEN_RESTROOM)
                .findFirst()
                .orElseThrow()
                .getId();
        String sessionSeed = "occupancy-change-" + System.nanoTime();

        List<OccupancyStatus> openStatuses = new ArrayList<>();
        for (int poll = 1; poll <= 20; poll++) {
            AmenityService.LiveStatus status = amenityService.checkAmenityStatus(
                    amenityId,
                    loader.getStartLocation().getLatitude(),
                    loader.getStartLocation().getLongitude(),
                    sessionSeed
            );
            if (status.status() == AmenityStatus.OPEN) {
                openStatuses.add(status.occupancyStatus());
            }
        }

        assertFalse(openStatuses.isEmpty());
        long distinctStatuses = openStatuses.stream().distinct().count();
        assertTrue(distinctStatuses > 1,
                "Occupancy status should change across polls; got: " + openStatuses);
    }

    @Test
    void occupancyLowStatusHasEnoughStallsAvailable() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        RouteMetricsService rms = TestServiceFactory.routeMetrics(loader);
        AmenityService amenityService = TestServiceFactory.amenityService(loader, rms);

        List<AmenityService.AmenityDistance> results = amenityService.getAmenitiesWithRouteDistance(
                null, null, null, false, "low-stalls-check-seed"
        );

        for (AmenityService.AmenityDistance ad : results) {
            AmenityService.LiveStatus live = ad.liveStatus();
            if (live.isSelectable() && live.occupancyStatus() == OccupancyStatus.LOW) {
                assertTrue(live.stallsAvailable() > 0,
                        "LOW occupancy must have stalls available");
                assertTrue(live.stallsAvailable() > 2,
                        "LOW occupancy should have more than 2 stalls");
            }
        }
    }
}
