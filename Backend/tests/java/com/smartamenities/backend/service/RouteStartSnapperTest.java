package com.smartamenities.backend.service;

import com.smartamenities.backend.model.RouteGeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteStartSnapperTest {

    @Test
    void snapsToNearestSegmentPoint() {
        List<List<RouteGeoPoint>> segments = List.of(
                List.of(
                        new RouteGeoPoint(32.000000, -97.000000),
                        new RouteGeoPoint(32.000000, -96.998000)
                ),
                List.of(
                        new RouteGeoPoint(32.002000, -97.001000),
                        new RouteGeoPoint(32.002000, -96.999000)
                )
        );

        RouteStartSnapper.SnapResult snapped = RouteStartSnapper.snapToNearestSegment(
                32.000600,
                -96.999200,
                segments
        );

        assertEquals(32.000000, snapped.latitude(), 0.000002);
        assertEquals(-96.999200, snapped.longitude(), 0.000002);
        assertTrue(snapped.snapDistanceMeters() > 0.0);
        assertTrue(snapped.snapDistanceMeters() < 80.0);
    }
}
