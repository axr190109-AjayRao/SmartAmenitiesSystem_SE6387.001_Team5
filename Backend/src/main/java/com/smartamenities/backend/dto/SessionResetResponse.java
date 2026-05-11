package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for POST /session/reset response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResetResponse {
    private String sessionSeed;
    private int clearedClosureTriggerCount;
    private boolean clearedSessionConfig;
}
