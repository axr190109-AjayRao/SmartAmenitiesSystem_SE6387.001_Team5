package com.smartamenities.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the route start anchor for navigation guidance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartLocation {
    private String name;
    private String level;
    private double latitude;
    private double longitude;
}
