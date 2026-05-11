package com.smartamenities.backend.integration.adminworkstation;

import com.smartamenities.backend.model.AmenityStatusReason;

/**
 * Per-amenity status report returned by the Admin Workstation adapter.
 * Distinct from InfrastructureStatusReport which covers route corridor blockages.
 */
public record AdminAmenityStatusReport(
        boolean amenityClosed,
        AmenityStatusReason reason,
        String alertId
) {

    public static AdminAmenityStatusReport open() {
        return new AdminAmenityStatusReport(false, null, null);
    }
}
