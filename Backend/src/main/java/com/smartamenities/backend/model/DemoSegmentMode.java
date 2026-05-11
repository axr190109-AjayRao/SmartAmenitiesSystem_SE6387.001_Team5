package com.smartamenities.backend.model;

/**
 * Demo segment mode used to gate simulated events so each segment
 * only surfaces the behavior it is meant to demonstrate.
 */
public enum DemoSegmentMode {
    LEGACY,
    SEGMENT_1,
    SEGMENT_2,
    SEGMENT_3,
    SEGMENT_4,
    SEGMENT_5;

    public boolean allowsAmenityClosure() {
        return this == LEGACY || this == SEGMENT_3;
    }

    public boolean allowsInfrastructureAlert() {
        return this == LEGACY || this == SEGMENT_4;
    }
}
