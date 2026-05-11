package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.AmenityResponse;
import com.smartamenities.backend.dto.AmenityLiveStatusResponse;
import com.smartamenities.backend.dto.AmenityReplacementResponse;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.service.AmenityService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * REST controller for amenities endpoints.
 */
@RestController
@RequestMapping("/amenities")
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    /**
     * GET /amenities
     * Returns a list of available amenities.
     *
     * @return list of amenities
     */
    @GetMapping
    public ResponseEntity<List<AmenityResponse>> getAmenities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double currentLat,
            @RequestParam(required = false) Double currentLon,
            @RequestParam(required = false) String sessionSeed
    ) {
        AmenityType amenityType = parseAmenityType(type);
        List<AmenityResponse> amenities = amenityService.getAmenitiesWithRouteDistance(amenityType, currentLat, currentLon, false, sessionSeed)
                .stream()
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(amenities);
    }

        @GetMapping("/live-status")
        public ResponseEntity<AmenityLiveStatusResponse> getAmenityLiveStatus(
            @RequestParam String amenityId,
            @RequestParam(required = false) Double currentLat,
            @RequestParam(required = false) Double currentLon,
            @RequestParam(required = false) String sessionSeed
        ) {
        AmenityService.LiveStatus liveStatus = amenityService.checkAmenityStatus(amenityId, currentLat, currentLon, sessionSeed);
        return ResponseEntity.ok(
            new AmenityLiveStatusResponse(
                amenityId,
                liveStatus.status(),
                liveStatus.statusReason(),
                liveStatus.waitTimeMinutes(),
                liveStatus.stallsAvailable(),
                liveStatus.occupancyStatus()
            )
        );
        }

        @GetMapping("/replacement")
        public ResponseEntity<AmenityReplacementResponse> getNearestOpenReplacement(
            @RequestParam String closedAmenityId,
            @RequestParam Double currentLat,
            @RequestParam Double currentLon,
            @RequestParam(required = false, defaultValue = "false") boolean accessibilityOn,
            @RequestParam(required = false) String sessionSeed
        ) {
        AmenityService.AmenityRecommendation recommendation = amenityService.findNearestOpenAmenitySameType(
            closedAmenityId,
            currentLat,
            currentLon,
            accessibilityOn,
            sessionSeed
        );

        return ResponseEntity.ok(
            new AmenityReplacementResponse(
                recommendation.amenity().getId(),
                recommendation.amenity().getDisplayName(),
                recommendation.amenity().getAmenityType(),
                recommendation.distanceMeters(),
                recommendation.liveStatus().waitTimeMinutes(),
                recommendation.liveStatus().stallsAvailable(),
                recommendation.liveStatus().occupancyStatus()
            )
        );
        }

    private AmenityType parseAmenityType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return AmenityType.valueOf(type.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported amenity type: " + type);
        }
    }

    private AmenityResponse toResponse(AmenityService.AmenityDistance amenityDistance) {
        var amenity = amenityDistance.amenity();

        return new AmenityResponse(
                amenity.getId(),
                amenity.getDisplayName(),
                amenity.getAmenityType(),
                amenity.getLevel(),
                amenity.getLatitude(),
                amenity.getLongitude(),
            amenityDistance.distanceMeters(),
            amenityDistance.liveStatus().status(),
            amenityDistance.liveStatus().statusReason(),
            amenityDistance.liveStatus().waitTimeMinutes(),
            amenityDistance.liveStatus().stallsAvailable(),
            amenityDistance.liveStatus().occupancyStatus(),
            amenityDistance.liveStatus().isSelectable()
        );
    }
}
