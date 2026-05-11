package com.smartamenities.backend.integration.location;

import com.smartamenities.backend.model.StartLocation;
import com.smartamenities.backend.service.GeoJsonAmenityLoader;
import com.smartamenities.backend.service.Level3SessionStartService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatedLocationProviderTest {

    @Test
    void sessionSeedUsesDeterministicSessionStartLocation() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        SimulatedLocationProvider provider = new SimulatedLocationProvider(new Level3SessionStartService(loader));

        StartLocation resolved = provider.resolveStartLocation(
                null,
                null,
                "session-alpha",
                loader.getStartLocation()
        );

        StartLocation expected = new Level3SessionStartService(loader).resolveStartLocation("session-alpha");
        assertEquals(expected.getLatitude(), resolved.getLatitude(), 0.000001);
        assertEquals(expected.getLongitude(), resolved.getLongitude(), 0.000001);
    }

    @Test
    void explicitCoordinatesOverrideDefaultStartWhenSessionMissing() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader();
        SimulatedLocationProvider provider = new SimulatedLocationProvider(new Level3SessionStartService(loader));

        StartLocation resolved = provider.resolveStartLocation(
                32.89963,
                -97.04045,
                null,
                loader.getStartLocation()
        );

        assertEquals("Custom Start", resolved.getName());
        assertEquals(32.89963, resolved.getLatitude(), 0.000001);
        assertEquals(-97.04045, resolved.getLongitude(), 0.000001);
    }
}
