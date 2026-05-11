package com.smartamenities.backend.service;

import com.smartamenities.backend.dto.InfrastructureRouteStatusRequest;
import com.smartamenities.backend.dto.InfrastructureRouteStatusResponse;
import com.smartamenities.backend.integration.adminworkstation.AdminWorkstationAdapter;
import com.smartamenities.backend.integration.adminworkstation.InfrastructureStatusReport;
import com.smartamenities.backend.integration.adminworkstation.InfrastructureStatusRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service layer for infrastructure status checks.
 * Delegates to the AdminWorkstationAdapter boundary to detect corridor
 * blockages on the passenger's active route.
 */
@Service
public class InfrastructureStatusService {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureStatusService.class);

    private final AdminWorkstationAdapter adminWorkstationAdapter;

    public InfrastructureStatusService(AdminWorkstationAdapter adminWorkstationAdapter) {
        this.adminWorkstationAdapter = adminWorkstationAdapter;
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

        log.info(
            "infrastructure.check segmentCount={} sessionSeed={}",
            segmentIds.size(), seed
        );

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
}
