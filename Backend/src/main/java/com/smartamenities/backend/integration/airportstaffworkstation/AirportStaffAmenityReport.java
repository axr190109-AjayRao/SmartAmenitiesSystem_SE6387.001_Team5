package com.smartamenities.backend.integration.airportstaffworkstation;

import com.smartamenities.backend.model.AmenityStatusReason;

/**
 * Report returned by the Airport Staff Workstation adapter describing the
 * current staff-observed status of a specific amenity.
 */
public record AirportStaffAmenityReport(
        boolean amenityClosed,
        AmenityStatusReason reason,
        String alertId,
        int waitTimeMinutes,
        int stallsAvailable,
        String reportingStaffZone
) {

    public static AirportStaffAmenityReport open(int waitTimeMinutes, int stallsAvailable, String zone) {
        return new AirportStaffAmenityReport(false, null, null, waitTimeMinutes, stallsAvailable, zone);
    }
}
