package com.smartamenities.backend.integration.adminworkstation;

/**
 * Report returned by the Admin Workstation adapter describing whether any
 * infrastructure blockage is active on the passenger's current route corridors.
 */
public record InfrastructureStatusReport(
        boolean corridorBlocked,
        String blockedSegmentId,
        String blockedSegmentDescription,
        String reason,
        String alertId,
        Integer estimatedClearanceMinutes
) {

    public static InfrastructureStatusReport clear() {
        return new InfrastructureStatusReport(false, null, null, null, null, null);
    }
}
