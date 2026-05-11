package com.smartamenities.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model for a geographic route point.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteGeoPoint {
    private double latitude;
    private double longitude;
}
