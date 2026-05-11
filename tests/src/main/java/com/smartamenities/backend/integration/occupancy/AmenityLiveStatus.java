package com.smartamenities.backend.integration.occupancy;

import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityStatusReason;
import com.smartamenities.backend.model.OccupancyStatus;

/**
 * External occupancy-system status payload mapped into backend domain terms.
 */
public record AmenityLiveStatus(
        AmenityStatus status,
        AmenityStatusReason statusReason,
        int waitTimeMinutes,
        int stallsAvailable,
        OccupancyStatus occupancyStatus
) {
}
