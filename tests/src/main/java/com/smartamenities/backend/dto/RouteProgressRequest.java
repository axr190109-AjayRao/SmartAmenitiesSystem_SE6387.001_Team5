package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for simulating progress along a route geometry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteProgressRequest {
    private List<RouteGeoPoint> routeGeoPoints;
    private List<RouteStepProgressRangeResponse> stepProgressRanges;
    private Double progressMeters;
    private Double totalDistanceMeters;
    private Double actualLatitude;
    private Double actualLongitude;
}
