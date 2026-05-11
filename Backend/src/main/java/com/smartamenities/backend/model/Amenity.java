package com.smartamenities.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model representing an amenity (e.g., Restroom, Lactation Room).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Amenity {
    private String id;
    private String displayName;
    private AmenityType amenityType;
    private String level;
    private double latitude;
    private double longitude;
}
