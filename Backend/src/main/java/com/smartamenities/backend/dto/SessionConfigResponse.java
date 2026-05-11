package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for POST /session/configure response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionConfigResponse {
    private String sessionSeed;
    private String segmentMode;
}
