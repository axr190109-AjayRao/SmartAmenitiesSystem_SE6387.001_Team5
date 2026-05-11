package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.SessionConfigResponse;
import com.smartamenities.backend.dto.SessionResetResponse;
import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.model.DemoSegmentMode;
import com.smartamenities.backend.service.AmenityService;
import com.smartamenities.backend.service.DemoSessionConfigService;
import com.smartamenities.backend.service.InfrastructureStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * REST controller for session lifecycle support used by demo automation.
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    private final AmenityService amenityService;
    private final DemoSessionConfigService demoSessionConfigService;
    private final InfrastructureStatusService infrastructureStatusService;

    public SessionController(
            AmenityService amenityService,
            DemoSessionConfigService demoSessionConfigService,
            InfrastructureStatusService infrastructureStatusService
    ) {
        this.amenityService = amenityService;
        this.demoSessionConfigService = demoSessionConfigService;
        this.infrastructureStatusService = infrastructureStatusService;
    }

    /**
     * POST /session/configure
     * Sets the demo segment mode for a session seed.
     */
    @PostMapping("/configure")
    public ResponseEntity<SessionConfigResponse> configureSession(
            @RequestParam String sessionSeed,
            @RequestParam String segment
    ) {
        if (sessionSeed == null || sessionSeed.isBlank()) {
            throw new BadRequestException("sessionSeed is required");
        }
        if (segment == null || segment.isBlank()) {
            throw new BadRequestException("segment is required");
        }

        DemoSegmentMode mode;
        try {
            mode = DemoSegmentMode.valueOf(segment.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported segment: " + segment);
        }

        DemoSegmentMode configured = demoSessionConfigService.configureSession(sessionSeed, mode);
        amenityService.resetSessionState(sessionSeed);
        infrastructureStatusService.clearSessionState(sessionSeed);
        return ResponseEntity.ok(new SessionConfigResponse(sessionSeed, configured.name()));
    }

    /**
     * POST /session/reset
     * Clears backend in-memory state associated with a completed demo session.
     *
     * @param sessionSeed completed session seed to clear
     * @return count of cleared closure trigger records
     */
    @PostMapping("/reset")
    public ResponseEntity<SessionResetResponse> resetSession(
            @RequestParam String sessionSeed
    ) {
        if (sessionSeed == null || sessionSeed.isBlank()) {
            throw new BadRequestException("sessionSeed is required");
        }

        AmenityService.SessionResetResult result = amenityService.resetSessionState(sessionSeed);
        boolean clearedSessionConfig = demoSessionConfigService.clearSession(sessionSeed);
        infrastructureStatusService.clearSessionState(sessionSeed);
        return ResponseEntity.ok(
                new SessionResetResponse(
                        result.sessionSeed(),
                        result.clearedClosureTriggerCount(),
                        clearedSessionConfig
                )
        );
    }
}
