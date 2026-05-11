package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for responding to POST /route endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private String destination;
    private boolean accessibilityOn;
    private String estimatedTime;
    private List<RouteStepResponse> routeSteps;
    private String startLocationName;
    private Double startLatitude;
    private Double startLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private List<RouteGeoPoint> routeGeoPoints;
    private Double totalDistanceMeters;
    private List<RouteSegmentResponse> routeSegments;
    private List<RouteStepProgressRangeResponse> stepProgressRanges;
}
