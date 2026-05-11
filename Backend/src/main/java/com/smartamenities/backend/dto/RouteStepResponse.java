package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a single navigation instruction line.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStepResponse {
    private String instruction;
}
