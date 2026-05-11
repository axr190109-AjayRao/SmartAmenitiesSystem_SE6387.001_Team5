package com.smartamenities.backend.dto;

import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityStatusReason;
import com.smartamenities.backend.model.OccupancyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for GET /amenities/live-status response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmenityLiveStatusResponse {
    private String amenityId;
    private AmenityStatus status;
    private AmenityStatusReason statusReason;
    private Integer waitTimeMinutes;
    private Integer stallsAvailable;
    private OccupancyStatus occupancyStatus;
}
