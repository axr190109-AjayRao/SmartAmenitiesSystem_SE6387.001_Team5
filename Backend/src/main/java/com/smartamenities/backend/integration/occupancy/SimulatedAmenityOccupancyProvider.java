package com.smartamenities.backend.integration.occupancy;

import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityStatusReason;
import com.smartamenities.backend.model.OccupancyStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Simulated occupancy sensor provider — models real-time stall availability and wait times.
 */
@Service
@Primary
public class SimulatedAmenityOccupancyProvider implements AmenityOccupancyProvider {

    @Override
    public AmenityLiveStatus getLiveStatus(Amenity amenity, Double currentLat, Double currentLon, String sessionSeed) {
        // stableHash drives per-amenity diversity — constant for a given amenity+session.
        int stableHash = Math.abs(Objects.hash(amenity.getId(), sessionSeed, amenity.getAmenityType().name()));
        LocalDateTime now = LocalDateTime.now();
        // timeHash changes every 30 seconds so values visibly update during navigation.
        int slot = now.getHour() * 120 + now.getMinute() * 2 + (now.getSecond() >= 30 ? 1 : 0);
        int timeHash = Math.abs(Objects.hash(amenity.getId(), slot));

        int totalStalls = estimateTotalStalls(amenity, stableHash);
        int trafficLoad = computeTrafficLoadPercent(now);
        // Asymmetric spread (-50..+20) pushes enough amenities into LOW range even at peak hours.
        int baseVariation = (stableHash % 71) - 50;
        // Gentle time drift (-10..+10) creates visible changes every 30 seconds during navigation.
        int timeFluctuation = (timeHash % 21) - 10;
        int pressurePercent = Math.min(90, Math.max(15, trafficLoad + baseVariation + timeFluctuation));

        int occupiedStalls = (int) Math.round(totalStalls * (pressurePercent / 100.0));
        int stallsAvailable = Math.max(0, totalStalls - occupiedStalls);

        int waitTimeMinutes;
        if (stallsAvailable > 0) {
            waitTimeMinutes = Math.max(0, (pressurePercent - 35) / 12);
        } else {
            // Queue starts building when no stalls are open.
            waitTimeMinutes = 3 + ((pressurePercent - 70) / 3);
        }
        waitTimeMinutes = Math.min(20, Math.max(0, waitTimeMinutes));

        OccupancyStatus occupancyStatus = resolveOccupancyStatus(waitTimeMinutes, stallsAvailable);

        return new AmenityLiveStatus(
                AmenityStatus.OPEN,
                null,
                waitTimeMinutes,
                stallsAvailable,
                occupancyStatus
        );
    }

    private static int estimateTotalStalls(Amenity amenity, int seedHash) {
        return switch (amenity.getAmenityType()) {
            case ACCESSIBLE_RESTROOM -> 2 + (seedHash % 3); // 2..4
            case MEN_RESTROOM, WOMEN_RESTROOM -> 6 + (seedHash % 7); // 6..12
        };
    }

    private static int computeTrafficLoadPercent(LocalDateTime now) {
        int hour = now.getHour();
        DayOfWeek day = now.getDayOfWeek();

        int base = switch (hour) {
            case 6, 7, 8, 11, 12, 13, 16, 17, 18, 19 -> 75;
            case 5, 9, 10, 14, 15, 20, 21 -> 60;
            default -> 42;
        };

        if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SUNDAY) {
            base += 8;
        } else if (day == DayOfWeek.SATURDAY) {
            base += 5;
        }

        return Math.min(95, Math.max(25, base));
    }

    private static OccupancyStatus resolveOccupancyStatus(int waitTimeMinutes, int stallsAvailable) {
        if (stallsAvailable == 0 || waitTimeMinutes >= 12) {
            return OccupancyStatus.FULL;
        }
        if (stallsAvailable <= 2 || waitTimeMinutes >= 8) {
            return OccupancyStatus.HIGH;
        }
        if (stallsAvailable <= 4 || waitTimeMinutes >= 4) {
            return OccupancyStatus.MEDIUM;
        }
        return OccupancyStatus.LOW;
    }
}
