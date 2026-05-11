package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a geographic point along a route.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteGeoPoint {
    private double latitude;
    private double longitude;
}
