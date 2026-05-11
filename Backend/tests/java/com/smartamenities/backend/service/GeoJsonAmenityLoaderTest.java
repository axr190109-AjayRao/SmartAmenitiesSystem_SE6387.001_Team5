package com.smartamenities.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoJsonAmenityLoaderTest {

    @Test
    void normalizeLabelCoversExpectedAmenityTypes() {
        assertEquals(AmenityType.WOMEN_RESTROOM, GeoJsonAmenityLoader.normalizeAmenityType("Women's Restroom"));
        assertEquals(AmenityType.WOMEN_RESTROOM, GeoJsonAmenityLoader.normalizeAmenityType("Womens Bathroom"));
        assertEquals(AmenityType.MEN_RESTROOM, GeoJsonAmenityLoader.normalizeAmenityType("Men's Bathroom"));
        assertEquals(AmenityType.ACCESSIBLE_RESTROOM, GeoJsonAmenityLoader.normalizeAmenityType("Accessible Restroom"));
        assertNull(GeoJsonAmenityLoader.normalizeAmenityType("Nursing Station"));
    }

    @Test
    void extractsAmenitiesFromLevel3GeoJson() {
        GeoJsonAmenityLoader loader = new GeoJsonAmenityLoader(new ObjectMapper());
        List<Amenity> amenities = loader.getAmenities();

        assertFalse(amenities.isEmpty());
        assertTrue(amenities.stream().anyMatch(a -> "Level3".equalsIgnoreCase(a.getLevel())));
        assertFalse(amenities.stream().anyMatch(a -> "Level1".equalsIgnoreCase(a.getLevel())));
        assertFalse(amenities.stream().anyMatch(a -> "Level4".equalsIgnoreCase(a.getLevel())));

        Amenity sample = amenities.get(0);
        assertNotNull(sample.getId());
        assertNotNull(sample.getAmenityType());
    }
}
