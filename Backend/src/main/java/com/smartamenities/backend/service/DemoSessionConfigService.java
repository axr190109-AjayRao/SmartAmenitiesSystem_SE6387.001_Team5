package com.smartamenities.backend.service;

import com.smartamenities.backend.model.DemoSegmentMode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores demo segment mode per session seed so simulated events can be gated
 * for deterministic, segment-specific demos.
 */
@Service
public class DemoSessionConfigService {

    private final Map<String, DemoSegmentMode> sessionModes = new ConcurrentHashMap<>();
    private final Map<String, String> sessionInitialDestinations = new ConcurrentHashMap<>();

    public DemoSegmentMode configureSession(String sessionSeed, DemoSegmentMode mode) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            return DemoSegmentMode.SEGMENT_1;
        }
        sessionModes.put(normalizedSeed, mode);
        sessionInitialDestinations.remove(normalizedSeed);
        return mode;
    }

    public DemoSegmentMode resolveMode(String sessionSeed) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            return DemoSegmentMode.SEGMENT_1;
        }
        return sessionModes.getOrDefault(normalizedSeed, DemoSegmentMode.SEGMENT_1);
    }

    public boolean clearSession(String sessionSeed) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            return false;
        }
        boolean clearedMode = sessionModes.remove(normalizedSeed) != null;
        boolean clearedDestination = sessionInitialDestinations.remove(normalizedSeed) != null;
        return clearedMode || clearedDestination;
    }

    public void recordInitialDestination(String sessionSeed, String destinationAmenityId) {
        String normalizedSeed = normalize(sessionSeed);
        String normalizedDestination = normalize(destinationAmenityId);
        if (normalizedSeed == null || normalizedDestination == null) {
            return;
        }
        sessionInitialDestinations.putIfAbsent(normalizedSeed, normalizedDestination);
    }

    public String getInitialDestination(String sessionSeed) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            return null;
        }
        return sessionInitialDestinations.get(normalizedSeed);
    }

    private static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
