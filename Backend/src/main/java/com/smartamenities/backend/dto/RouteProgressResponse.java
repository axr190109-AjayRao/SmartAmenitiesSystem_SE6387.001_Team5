package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for simulated progress queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteProgressResponse {
    private double snappedLatitude;
    private double snappedLongitude;
    private double clampedProgressMeters;
    private int activeStepIndex;
    private String activeInstruction;
    private double remainingDistanceMeters;
    private List<String> remainingSteps;
    private boolean arrived;
    private boolean offRoute;
    private double deviationMeters;
}
