package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for receiving POST /route request body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {
    private String destination;
    private String currentLocation;
    private boolean accessibilityOn;
    private String destinationAmenityId;
    private Double currentLatitude;
    private Double currentLongitude;
}
