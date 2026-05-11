package com.smartamenities.backend.integration.airportstaffworkstation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Simulated Airport Staff Workstation adapter.
 * Staff observations are seeded by amenityId + sessionSeed for consistent results
 * within the same navigation session. Per-amenity closures are coordinated by
 * AmenityService's ClosureTriggerState so exactly one source fires per session —
 * this adapter supplies staff-observed occupancy data on non-closure ticks.
 */
@Service
@Primary
public class SimulatedAirportStaffWorkstationAdapter implements AirportStaffWorkstationAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAirportStaffWorkstationAdapter.class);

    private static final String[] STAFF_ZONES = {
        "Zone-A", "Zone-B", "Zone-C", "Zone-D"
    };

    @Override
    public AirportStaffAmenityReport getAmenityReport(AirportStaffAmenityRequest request) {
        int seedHash = Math.abs(Objects.hash(request.amenityId(), request.sessionSeed()));
        int staffObservedWait = seedHash % 4;
        int staffObservedStalls = 2 + (seedHash % 5);
        String zone = STAFF_ZONES[seedHash % STAFF_ZONES.length];

        log.debug(
            "staff-workstation.simulated: amenityId={} zone={} observedWait={}m observedStalls={}",
            request.amenityId(), zone, staffObservedWait, staffObservedStalls
        );

        return AirportStaffAmenityReport.open(staffObservedWait, staffObservedStalls, zone);
    }
}
