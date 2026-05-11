package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a single ordered route segment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteSegmentResponse {
    private int segmentIndex;
    private double fromLat;
    private double fromLon;
    private double toLat;
    private double toLon;
    private double lengthMeters;
    private double cumulativeStartMeters;
    private double cumulativeEndMeters;
    private int stepIndex;
}
