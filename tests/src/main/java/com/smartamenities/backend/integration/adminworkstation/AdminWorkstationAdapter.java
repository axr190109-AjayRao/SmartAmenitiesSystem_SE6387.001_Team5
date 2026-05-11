package com.smartamenities.backend.integration.adminworkstation;

/**
 * Boundary interface for the Admin Workstation external system.
 * Covers both infrastructure corridor status and per-amenity administrative closures.
 */
public interface AdminWorkstationAdapter {

    InfrastructureStatusReport checkRouteCorridors(InfrastructureStatusRequest request);

    AdminAmenityStatusReport checkAmenityStatus(String amenityId, String sessionSeed);
}
