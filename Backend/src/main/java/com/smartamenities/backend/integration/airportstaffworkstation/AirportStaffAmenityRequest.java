package com.smartamenities.backend.integration.airportstaffworkstation;

/**
 * Request sent to the Airport Staff Workstation adapter to check the
 * staff-reported status of a specific amenity.
 */
public record AirportStaffAmenityRequest(
        String amenityId,
        String sessionSeed
) {
}
