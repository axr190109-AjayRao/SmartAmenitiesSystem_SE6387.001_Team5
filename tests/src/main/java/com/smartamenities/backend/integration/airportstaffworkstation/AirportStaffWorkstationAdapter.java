package com.smartamenities.backend.integration.airportstaffworkstation;

/**
 * Boundary interface for the Airport Staff Workstation external system.
 * Represents real-time amenity status reports submitted by airport ground staff,
 * such as cleaning closures, temporary incidents, or direct stall-count observations.
 */
public interface AirportStaffWorkstationAdapter {

    AirportStaffAmenityReport getAmenityReport(AirportStaffAmenityRequest request);
}
