package com.smartamenities.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for POST /route/reroute.
 * Requires the user's actual coordinates and the destination amenity id.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerouteRequest {
    private Double currentLatitude;
    private Double currentLongitude;
    private String destinationAmenityId;
    private boolean accessibilityOn;
    private List<String> avoidSegments;
}
