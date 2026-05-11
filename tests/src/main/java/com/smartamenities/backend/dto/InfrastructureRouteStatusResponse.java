package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for POST /infrastructure/route-status response.
 * Reports whether any corridor on the passenger's active route is blocked,
 * and provides human-readable details for the mandatory reroute popup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureRouteStatusResponse {
    private boolean corridorBlocked;
    private String blockedSegmentId;
    private String blockedSegmentDescription;
    private String reason;
    private String alertId;
    private Integer estimatedClearanceMinutes;
}
