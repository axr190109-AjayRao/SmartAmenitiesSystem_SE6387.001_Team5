package com.smartamenities.backend.service;

import com.smartamenities.backend.dto.InfrastructureRouteStatusRequest;
import com.smartamenities.backend.dto.InfrastructureRouteStatusResponse;
import com.smartamenities.backend.integration.adminworkstation.AdminWorkstationAdapter;
import com.smartamenities.backend.integration.adminworkstation.InfrastructureStatusReport;
import com.smartamenities.backend.integration.adminworkstation.InfrastructureStatusRequest;
import com.smartamenities.backend.model.DemoSegmentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service layer for infrastructure status checks.
 * Delegates to the AdminWorkstationAdapter boundary to detect corridor
 * blockages on the passenger's active route.
 */
@Service
public class InfrastructureStatusService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureStatusService.class);
    private static final int MID_MIN_POLL = 2;
    private static final int MID_MAX_POLL = 3;

    private final AdminWorkstationAdapter adminWorkstationAdapter;
    private final DemoSessionConfigService demoSessionConfigService;
    private final Map<String, InfrastructureTriggerState> infrastructureTriggers = new ConcurrentHashMap<>();

    public InfrastructureStatusService(
            AdminWorkstationAdapter adminWorkstationAdapter,
            DemoSessionConfigService demoSessionConfigService
    ) {
        this.adminWorkstationAdapter = adminWorkstationAdapter;
        this.demoSessionConfigService = demoSessionConfigService;
    }

    public InfrastructureRouteStatusResponse checkRouteStatus(
            InfrastructureRouteStatusRequest request,
            String sessionSeed
    ) {
        List<String> segmentIds = request.getSegmentIds() != null
                ? request.getSegmentIds()
                : Collections.emptyList();

        String seed = sessionSeed != null ? sessionSeed
                : (request.getSessionSeed() != null ? request.getSessionSeed() : null);
        DemoSegmentMode mode = demoSessionConfigService.resolveMode(seed);

        log.info(
            "infrastructure.check segmentCount={} sessionSeed={} mode={}",
            segmentIds.size(), seed, mode
        );

        if (!mode.allowsInfrastructureAlert()) {
            return new InfrastructureRouteStatusResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        String normalizedSeed = normalize(seed);
        String triggerKey = normalizedSeed == null ? "anonymous" : normalizedSeed;
        InfrastructureTriggerState triggerState = infrastructureTriggers.computeIfAbsent(
                triggerKey,
                key -> createInfrastructureTriggerState(normalizedSeed)
        );

        boolean shouldEmitAlert;
        synchronized (triggerState) {
            triggerState.pollCount += 1;
            shouldEmitAlert = !triggerState.delivered && triggerState.pollCount >= triggerState.triggerPoll;
            if (shouldEmitAlert) {
                triggerState.delivered = true;
            }
        }

        if (!shouldEmitAlert) {
            return new InfrastructureRouteStatusResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        InfrastructureStatusReport report = adminWorkstationAdapter.checkRouteCorridors(
                new InfrastructureStatusRequest(segmentIds, seed)
        );

        log.info(
            "infrastructure.result corridorBlocked={} alertId={} reason={}",
            report.corridorBlocked(), report.alertId(), report.reason()
        );

        return new InfrastructureRouteStatusResponse(
                report.corridorBlocked(),
                report.blockedSegmentId(),
                report.blockedSegmentDescription(),
                report.reason(),
                report.alertId(),
                report.estimatedClearanceMinutes()
        );
    }

    public boolean clearSessionState(String sessionSeed) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            return infrastructureTriggers.remove("anonymous") != null;
        }
        return infrastructureTriggers.remove(normalizedSeed) != null;
    }

    private static InfrastructureTriggerState createInfrastructureTriggerState(String sessionSeed) {
        int hash = Math.floorMod(Objects.hash(sessionSeed, "infra"), Integer.MAX_VALUE);
        int span = MID_MAX_POLL - MID_MIN_POLL + 1;
        int triggerPoll = MID_MIN_POLL + (hash % span);
        return new InfrastructureTriggerState(triggerPoll);
    }

    private static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class InfrastructureTriggerState {
        private final int triggerPoll;
        private int pollCount;
        private boolean delivered;

        private InfrastructureTriggerState(int triggerPoll) {
            this.triggerPoll = triggerPoll;
            this.pollCount = 0;
            this.delivered = false;
        }
    }
}
