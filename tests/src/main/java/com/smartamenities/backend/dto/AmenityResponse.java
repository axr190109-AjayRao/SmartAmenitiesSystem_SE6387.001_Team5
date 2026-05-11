package com.smartamenities.backend.dto;

import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityStatusReason;
import com.smartamenities.backend.model.OccupancyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for responding to GET /amenities endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmenityResponse {
    private String id;
    private String name;
    private AmenityType amenityType;
    private String level;
    private double latitude;
    private double longitude;
    private Double distanceMeters;
    private AmenityStatus status;
    private AmenityStatusReason statusReason;
    private Integer waitTimeMinutes;
    private Integer stallsAvailable;
    private OccupancyStatus occupancyStatus;
    private Boolean selectable;
}
