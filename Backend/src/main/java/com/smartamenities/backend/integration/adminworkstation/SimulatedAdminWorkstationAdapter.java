package com.smartamenities.backend.integration.adminworkstation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Simulated Admin Workstation external system.
 *
 * Mirrors the pattern of SimulatedWayfinderProvider: in-process stand-in for a
 * real Admin Workstation integration. Always reports a corridor blockage on every
 * call so the infrastructure closure feature is demonstrable on every run.
 * The blocked segment and reason are chosen deterministically from the session seed.
 */
@Service
@Primary
public class SimulatedAdminWorkstationAdapter implements AdminWorkstationAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAdminWorkstationAdapter.class);

    private static final String[] BLOCKED_REASONS = {
        "Maintenance crew blocking hallway",
        "Spill cleanup in progress",
        "Emergency equipment staged in corridor",
        "Temporary construction barrier"
    };

    private static final String[] SEGMENT_DESCRIPTIONS = {
        "Corridor near central junction",
        "Hallway approaching gate area",
        "Passage between concourse sections",
        "Access corridor to amenity wing"
    };

    @Override
    public InfrastructureStatusReport checkRouteCorridors(InfrastructureStatusRequest request) {
        if (request.segmentIds() == null || request.segmentIds().isEmpty()) {
            return InfrastructureStatusReport.clear();
        }

        int seedHash = sessionSeedHash(request.sessionSeed());

        String blockedSegmentId = request.segmentIds().get(seedHash % request.segmentIds().size());
        String reason = BLOCKED_REASONS[seedHash % BLOCKED_REASONS.length];
        String description = SEGMENT_DESCRIPTIONS[seedHash % SEGMENT_DESCRIPTIONS.length];
        String alertId = "INFRA-" + UUID.nameUUIDFromBytes(
            (request.sessionSeed() + blockedSegmentId).getBytes()
        ).toString().substring(0, 8).toUpperCase();

        log.info(
            "admin-workstation.simulated: corridor blocked segmentId={} description={} reason={} alertId={}",
            blockedSegmentId, description, reason, alertId
        );

        return new InfrastructureStatusReport(
            true,
            blockedSegmentId,
            description,
            reason,
            alertId,
            5
        );
    }

    @Override
    public AdminAmenityStatusReport checkAmenityStatus(String amenityId, String sessionSeed) {
        // In the simulation, per-amenity admin closures are coordinated by AmenityService's
        // ClosureTriggerState so exactly one source fires per session. This method returns
        // the admin perspective for the data-merge path (non-closure ticks).
        return AdminAmenityStatusReport.open();
    }

    private static int sessionSeedHash(String sessionSeed) {
        if (sessionSeed == null || sessionSeed.isBlank()) {
            return 1;
        }
        int hash = 0;
        for (char c : sessionSeed.toCharArray()) {
            hash = hash * 31 + c;
        }
        return Math.abs(hash);
    }
}
