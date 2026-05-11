package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO describing the progress range covered by a route step.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStepProgressRangeResponse {
    private int stepIndex;
    private double startMeters;
    private double endMeters;
    private String instruction;
}
