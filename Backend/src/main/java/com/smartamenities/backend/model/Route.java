package com.smartamenities.backend.model;

import com.smartamenities.backend.routing.RouteSegmentProgress;
import com.smartamenities.backend.routing.RouteStepProgressRange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain model representing a calculated route from current location to destination.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Route {
    private String destination;
    private String currentLocation;
    private String startLocationName;
    private boolean accessibilityOn;
    private String estimatedTime;
    private List<String> routeSteps;
    private Double startLatitude;
    private Double startLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private List<RouteGeoPoint> routeGeoPoints;
    private Double totalDistanceMeters;
    private List<RouteSegmentProgress> routeSegments;
    private List<RouteStepProgressRange> stepProgressRanges;
}
