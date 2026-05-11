package com.smartamenities.backend.integration.adminworkstation;

import java.util.List;

/**
 * Canonical request sent to the Admin Workstation adapter to check whether
 * any segments along the passenger's current route are infrastructure-blocked.
 */
public record InfrastructureStatusRequest(
        List<String> segmentIds,
        String sessionSeed
) {
}
