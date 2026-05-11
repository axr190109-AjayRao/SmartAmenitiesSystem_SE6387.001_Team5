package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for POST /infrastructure/route-status request body.
 * Contains the segment IDs from the passenger's active route so the
 * Admin Workstation adapter can check for corridor blockages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureRouteStatusRequest {
    private List<String> segmentIds;
    private String sessionSeed;
}
