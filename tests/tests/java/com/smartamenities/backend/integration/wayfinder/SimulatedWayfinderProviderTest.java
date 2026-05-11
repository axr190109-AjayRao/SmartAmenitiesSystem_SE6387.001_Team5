package com.smartamenities.backend.integration.wayfinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulatedWayfinderProviderTest {

    @Test
    void providerNormalizesRequestAndUsesDefaultLocationFallback() {
        SimulatedWayfinderProvider provider = new SimulatedWayfinderProvider();

        ResolvedWayfinderRequest resolved = provider.resolveRequest(
                "  ACC-RESTROOM-01  ",
                "  Accessible Restroom  ",
                "   ",
                "Terminal D Departures"
        );

        assertEquals("ACC-RESTROOM-01", resolved.destinationAmenityId());
        assertEquals("Accessible Restroom", resolved.destinationLabel());
        assertEquals("Terminal D Departures", resolved.currentLocation());
    }

    @Test
    void providerAllowsNullDestinationIdAndTrimmedCurrentLocation() {
        SimulatedWayfinderProvider provider = new SimulatedWayfinderProvider();

        ResolvedWayfinderRequest resolved = provider.resolveRequest(
                null,
                "Women Restroom",
                "  Gate D22 ",
                "Terminal D Departures"
        );

        assertNull(resolved.destinationAmenityId());
        assertEquals("Women Restroom", resolved.destinationLabel());
        assertEquals("Gate D22", resolved.currentLocation());
    }
}
