package com.smartamenities.backend;

import com.smartamenities.backend.integration.adminworkstation.SimulatedAdminWorkstationAdapter;
import com.smartamenities.backend.integration.airportstaffworkstation.SimulatedAirportStaffWorkstationAdapter;
import com.smartamenities.backend.integration.location.SimulatedLocationProvider;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.integration.occupancy.SimulatedAmenityOccupancyProvider;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.Level3SessionStartService;
import com.smartamenities.backend.service.RouteMetricsService;

/**
 * Constructs fully-wired service instances for use in unit tests.
 * All references to Simulated* concrete classes are concentrated here so that
 * service files depend only on integration interfaces, not implementations.
 */
public class TestServiceFactory {

    public static RouteMetricsService routeMetrics(GeoJsonAmenityLoader loader) {
        return new RouteMetricsService(
                loader,
                new SimulatedLocationProvider(new Level3SessionStartService(loader))
        );
    }

    public static AmenityService amenityService(GeoJsonAmenityLoader loader, RouteMetricsService rms) {
        return new AmenityService(
                loader, rms,
                new SimulatedAmenityOccupancyProvider(),
                new SimulatedAdminWorkstationAdapter(),
                new SimulatedAirportStaffWorkstationAdapter()
        );
    }

    public static AmenityService amenityService(
            GeoJsonAmenityLoader loader,
            RouteMetricsService rms,
            AmenityOccupancyProvider occupancyProvider
    ) {
        return new AmenityService(
                loader, rms, occupancyProvider,
                new SimulatedAdminWorkstationAdapter(),
                new SimulatedAirportStaffWorkstationAdapter()
        );
    }
}
