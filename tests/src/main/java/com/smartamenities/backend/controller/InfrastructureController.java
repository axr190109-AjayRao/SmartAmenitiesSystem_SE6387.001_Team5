package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.InfrastructureRouteStatusRequest;
import com.smartamenities.backend.dto.InfrastructureRouteStatusResponse;
import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.service.InfrastructureStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for infrastructure status endpoints.
 * Polls the Admin Workstation adapter to detect corridor blockages
 * on the passenger's active navigation route.
 */
@RestController
@RequestMapping("/infrastructure")
public class InfrastructureController {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureController.class);

    private final InfrastructureStatusService infrastructureStatusService;

    public InfrastructureController(InfrastructureStatusService infrastructureStatusService) {
        this.infrastructureStatusService = infrastructureStatusService;
    }

    /**
     * POST /infrastructure/route-status
     * Checks whether any corridor segments on the passenger's active route
     * are currently blocked according to the Admin Workstation.
     *
     * @param sessionSeed optional session token for simulation seeding
     * @param request     contains the ordered list of route segment IDs to check
     * @return infrastructure status report
     */
    @PostMapping("/route-status")
    public ResponseEntity<InfrastructureRouteStatusResponse> checkRouteStatus(
            @RequestParam(required = false) String sessionSeed,
            @RequestBody InfrastructureRouteStatusRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getSegmentIds() == null || request.getSegmentIds().isEmpty()) {
            throw new BadRequestException("segmentIds must contain at least one segment");
        }

        return ResponseEntity.ok(
                infrastructureStatusService.checkRouteStatus(request, sessionSeed)
        );
    }
}
