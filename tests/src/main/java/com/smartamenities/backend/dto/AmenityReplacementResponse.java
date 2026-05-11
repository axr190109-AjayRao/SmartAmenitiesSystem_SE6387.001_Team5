package com.smartamenities.backend.dto;

import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.OccupancyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for GET /amenities/replacement response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmenityReplacementResponse {
    private String amenityId;
    private String amenityName;
    private AmenityType amenityType;
    private Double distanceMeters;
    private Integer waitTimeMinutes;
    private Integer stallsAvailable;
    private OccupancyStatus occupancyStatus;
}
