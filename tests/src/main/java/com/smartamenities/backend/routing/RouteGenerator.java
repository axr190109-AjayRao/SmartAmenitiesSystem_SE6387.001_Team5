package com.smartamenities.backend.routing;

import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.RouteGeoPoint;

import java.util.List;

/**
 * Route step generator.
 * Delegates to TurnBasedRouteGuider for geometry-based turn-by-turn guidance.
 */
public class RouteGenerator {

    /**
     * Generate turn-by-turn route steps derived from corridor geometry.
     *
     * @param currentLocation starting point name
     * @param destinationAmenity endpoint amenity
     * @param accessibilityOn if true, use accessible route wording
     * @param routeGeoPoints ordered polyline points
     * @param totalMeters total route distance
     * @param destinationConnectorMeters distance from corridor to amenity anchor
     * @return list of turn-by-turn instruction strings
     */
    public static List<String> generateRouteSteps(
            String currentLocation,
            Amenity destinationAmenity,
            boolean accessibilityOn,
            List<RouteGeoPoint> routeGeoPoints,
            double totalMeters,
            double destinationConnectorMeters
    ) {
        return generateRouteGuidancePlan(
            currentLocation,
            destinationAmenity,
            accessibilityOn,
            routeGeoPoints,
            totalMeters,
            destinationConnectorMeters
        ).routeSteps();
        }

        public static RouteGuidancePlan generateRouteGuidancePlan(
            String currentLocation,
            Amenity destinationAmenity,
            boolean accessibilityOn,
            List<RouteGeoPoint> routeGeoPoints,
            double totalMeters,
            double destinationConnectorMeters
        ) {
        return TurnBasedRouteGuider.buildGuidancePlan(
                routeGeoPoints,
                totalMeters,
                destinationConnectorMeters,
                accessibilityOn,
            currentLocation,
                destinationAmenity.getDisplayName()
        );
    }
}
