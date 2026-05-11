package com.smartamenities.backend.service;

import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.exception.ResourceNotFoundException;
import com.smartamenities.backend.integration.adminworkstation.AdminWorkstationAdapter;
import com.smartamenities.backend.integration.adminworkstation.AdminAmenityStatusReport;
import com.smartamenities.backend.integration.occupancy.AmenityLiveStatus;
import com.smartamenities.backend.integration.occupancy.AmenityOccupancyProvider;
import com.smartamenities.backend.integration.airportstaffworkstation.AirportStaffAmenityReport;
import com.smartamenities.backend.integration.airportstaffworkstation.AirportStaffAmenityRequest;
import com.smartamenities.backend.integration.airportstaffworkstation.AirportStaffWorkstationAdapter;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityStatus;
import com.smartamenities.backend.model.AmenityStatusReason;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.DemoSegmentMode;
import com.smartamenities.backend.model.OccupancyStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application service for amenity lookup, filtering, and ranking.
 */
@Service
public class AmenityService {
    private static final int EARLY_MIN_POLL = 1;
    private static final int EARLY_MAX_POLL = 3;
    private static final int MID_MIN_POLL = 4;
    private static final int MID_MAX_POLL = 7;
    private static final int LATE_MIN_POLL = 8;
    private static final int LATE_MAX_POLL = 12;
    private static final int SEGMENT_EVENT_MIN_POLL = 2;
    private static final int SEGMENT_EVENT_MAX_POLL = 3;

    private final GeoJsonAmenityLoader geoJsonAmenityLoader;
    private final RouteMetricsService routeMetricsService;
    private final AmenityOccupancyProvider amenityOccupancyProvider;
    private final AdminWorkstationAdapter adminWorkstationAdapter;
    private final AirportStaffWorkstationAdapter airportStaffWorkstationAdapter;
    private final DemoSessionConfigService demoSessionConfigService;
    private final Map<String, ClosureTriggerState> closureTriggers = new ConcurrentHashMap<>();

    @Autowired
    public AmenityService(
            GeoJsonAmenityLoader geoJsonAmenityLoader,
            RouteMetricsService routeMetricsService,
            AmenityOccupancyProvider amenityOccupancyProvider,
            AdminWorkstationAdapter adminWorkstationAdapter,
            AirportStaffWorkstationAdapter airportStaffWorkstationAdapter,
            DemoSessionConfigService demoSessionConfigService
    ) {
        this.geoJsonAmenityLoader = geoJsonAmenityLoader;
        this.routeMetricsService = routeMetricsService;
        this.amenityOccupancyProvider = amenityOccupancyProvider;
        this.adminWorkstationAdapter = adminWorkstationAdapter;
        this.airportStaffWorkstationAdapter = airportStaffWorkstationAdapter;
        this.demoSessionConfigService = demoSessionConfigService;
    }

    public List<Amenity> getAmenities(AmenityType type, Double currentLat, Double currentLon) {
        return getAmenitiesWithRouteDistance(type, currentLat, currentLon, false, null)
                .stream()
                .map(AmenityDistance::amenity)
                .toList();
    }

    public List<Amenity> getAmenities(AmenityType type, Double currentLat, Double currentLon, String sessionSeed) {
        return getAmenitiesWithRouteDistance(type, currentLat, currentLon, false, sessionSeed)
                .stream()
                .map(AmenityDistance::amenity)
                .toList();
    }

    public List<AmenityDistance> getAmenitiesWithRouteDistance(
            AmenityType type,
            Double currentLat,
            Double currentLon,
            boolean accessibilityOn
    ) {
        return getAmenitiesWithRouteDistance(type, currentLat, currentLon, accessibilityOn, null);
    }

    public List<AmenityDistance> getAmenitiesWithRouteDistance(
            AmenityType type,
            Double currentLat,
            Double currentLon,
            boolean accessibilityOn,
            String sessionSeed
    ) {
        List<Amenity> filtered = geoJsonAmenityLoader.getAmenities().stream()
                .filter(amenity -> type == null || amenity.getAmenityType() == type)
                .collect(Collectors.toList());

        List<AmenityDistance> ranked = new ArrayList<>(filtered.size());
        for (Amenity amenity : filtered) {
            Double distanceMeters;
            LiveStatus liveStatus = mapLiveStatus(
                    amenityOccupancyProvider.getLiveStatus(amenity, currentLat, currentLon, sessionSeed)
            );
            try {
                RouteMetricsService.RouteComputation routeComputation = routeMetricsService.computeRouteMetrics(
                        amenity,
                        accessibilityOn,
                        currentLat,
                        currentLon,
                        sessionSeed
                );
                distanceMeters = routeComputation.routeDistanceMeters();
            } catch (IllegalStateException ex) {
                distanceMeters = null;
            }
            ranked.add(new AmenityDistance(amenity, distanceMeters, liveStatus));
        }

        // Guarantee at least one closed amenity per session so the amenity list always shows a non-selectable entry.
        boolean hasAnyClosed = ranked.stream().anyMatch(ad -> !ad.liveStatus().isSelectable());
        if (!hasAnyClosed) {
            String anchor = (sessionSeed != null && !sessionSeed.isBlank()) ? sessionSeed : "default";
            int closureIndex = Math.abs(Objects.hash(anchor)) % ranked.size();
            AmenityDistance toClose = ranked.get(closureIndex);
            AmenityStatusReason reason = (Math.abs(Objects.hash(anchor, "reason")) % 2 == 0)
                    ? AmenityStatusReason.MAINTENANCE
                    : AmenityStatusReason.CONSTRUCTION;
            ranked.set(closureIndex, new AmenityDistance(
                    toClose.amenity(),
                    toClose.distanceMeters(),
                    new LiveStatus(AmenityStatus.CLOSED, reason, 0, 0, OccupancyStatus.FULL)
            ));
        }

        ranked.sort(
            Comparator.<AmenityDistance>comparingInt(entry -> entry.liveStatus().isSelectable() ? 0 : 1)
                        .thenComparingDouble(AmenityService::compositeRankingScore)
                        .thenComparing(entry -> entry.amenity().getId())
        );

        return ranked;
    }

    public LiveStatus checkAmenityStatus(String amenityId, Double currentLat, Double currentLon, String sessionSeed) {
        Amenity amenity = resolveAmenityById(amenityId);
        String normalizedSeed = normalize(sessionSeed);
        DemoSegmentMode mode = demoSessionConfigService.resolveMode(normalizedSeed);
        if (!mode.allowsAmenityClosure()) {
            return aggregateLiveStatus(amenity, currentLat, currentLon, normalizedSeed);
        }

        String triggerKey = (normalizedSeed == null ? "anonymous" : normalizedSeed) + "::" + amenity.getId();
        ClosureTriggerState triggerState = closureTriggers.computeIfAbsent(
                triggerKey,
                key -> createClosureTriggerState(normalizedSeed, amenity.getId(), mode)
        );

        boolean shouldForceClosure;
        ClosureSource closureSource;
        synchronized (triggerState) {
            triggerState.pollCount += 1;
            shouldForceClosure = !triggerState.delivered && triggerState.pollCount >= triggerState.triggerPoll;
            if (shouldForceClosure) {
                triggerState.delivered = true;
            }
            closureSource = triggerState.source;
        }

        // Demo behavior: one closure alert per session at a randomized route tick.
        // The source (admin / staff / occupancy) is assigned at trigger-state creation.
        if (shouldForceClosure) {
            return closureStatusForSource(closureSource);
        }

        // Non-trigger ticks: aggregate live data from all three sources.
        return aggregateLiveStatus(amenity, currentLat, currentLon, normalizedSeed);
    }

    private LiveStatus closureStatusForSource(ClosureSource source) {
        return switch (source) {
            case ADMIN -> new LiveStatus(
                    AmenityStatus.CLOSED,
                    AmenityStatusReason.MAINTENANCE,
                    0,
                    0,
                    OccupancyStatus.FULL
            );
            case STAFF -> new LiveStatus(
                    AmenityStatus.CLOSED,
                    AmenityStatusReason.CLEANING,
                    0,
                    0,
                    OccupancyStatus.FULL
            );
            case OCCUPANCY -> new LiveStatus(
                    AmenityStatus.CLOSED,
                    AmenityStatusReason.CONSTRUCTION,
                    0,
                    0,
                    OccupancyStatus.FULL
            );
        };
    }

    /**
     * Queries all three live-status sources and merges by priority:
     * admin closure > staff closure > occupancy closure > OPEN with occupancy data.
     */
    private LiveStatus aggregateLiveStatus(
            Amenity amenity,
            Double currentLat,
            Double currentLon,
            String sessionSeed
    ) {
        AdminAmenityStatusReport adminReport =
                adminWorkstationAdapter.checkAmenityStatus(amenity.getId(), sessionSeed);
        AirportStaffAmenityReport staffReport =
                airportStaffWorkstationAdapter.getAmenityReport(
                        new AirportStaffAmenityRequest(amenity.getId(), sessionSeed)
                );
        AmenityLiveStatus occupancyReport =
                amenityOccupancyProvider.getLiveStatus(amenity, currentLat, currentLon, sessionSeed);

        if (adminReport.amenityClosed()) {
            return new LiveStatus(
                    AmenityStatus.CLOSED,
                    adminReport.reason(),
                    0,
                    0,
                    OccupancyStatus.FULL
            );
        }
        if (staffReport.amenityClosed()) {
            return new LiveStatus(
                    AmenityStatus.CLOSED,
                    staffReport.reason(),
                    0,
                    0,
                    OccupancyStatus.FULL
            );
        }
        if (occupancyReport.status() == AmenityStatus.CLOSED) {
            return mapLiveStatus(occupancyReport);
        }

        // All sources open — use occupancy sensor for quantitative fields.
        return mapLiveStatus(occupancyReport);
    }

    public SessionResetResult resetSessionState(String sessionSeed) {
        String normalizedSeed = normalize(sessionSeed);
        if (normalizedSeed == null) {
            throw new BadRequestException("sessionSeed is required");
        }

        String keyPrefix = normalizedSeed + "::";
        List<String> keysToRemove = closureTriggers.keySet().stream()
                .filter(key -> key.startsWith(keyPrefix))
                .toList();

        keysToRemove.forEach(closureTriggers::remove);
        return new SessionResetResult(normalizedSeed, keysToRemove.size());
    }

    public AmenityRecommendation findNearestOpenAmenitySameType(
            String closedAmenityId,
            Double currentLat,
            Double currentLon,
            boolean accessibilityOn,
            String sessionSeed
    ) {
        Amenity closedAmenity = resolveAmenityById(closedAmenityId);
        List<AmenityDistance> candidates = getAmenitiesWithRouteDistance(
                closedAmenity.getAmenityType(),
                currentLat,
                currentLon,
                accessibilityOn,
                sessionSeed
        );

        return candidates.stream()
                .filter(entry -> !entry.amenity().getId().equals(closedAmenityId))
                .filter(entry -> entry.liveStatus().isSelectable())
                .min(
                    Comparator.<AmenityDistance, Double>comparing(AmenityDistance::distanceMeters, Comparator.nullsLast(Double::compareTo))
                                .thenComparingInt(entry -> entry.liveStatus().waitTimeMinutes())
                                .thenComparing((left, right) -> Integer.compare(
                                        right.liveStatus().stallsAvailable(),
                                        left.liveStatus().stallsAvailable()
                                ))
                )
                .map(entry -> new AmenityRecommendation(entry.amenity(), entry.distanceMeters(), entry.liveStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No open replacement amenity found for " + closedAmenityId
                ));
    }

    public DestinationResolution resolveDestinationForRoute(String destinationAmenityId, String destinationLabel) {
        String normalizedId = normalize(destinationAmenityId);
        String normalizedDestination = normalize(destinationLabel);

        if (normalizedId == null && normalizedDestination == null) {
            throw new BadRequestException("Either destinationAmenityId or destination must be provided");
        }

        if (normalizedId != null) {
            Amenity idMatch = geoJsonAmenityLoader.getAmenities().stream()
                    .filter(amenity -> amenity.getId().equals(normalizedId))
                    .findFirst()
                    .orElse(null);
            if (idMatch != null) {
                return new DestinationResolution(idMatch, "id-based", true, true, false);
            }
            if (normalizedDestination != null) {
                Amenity fallbackMatch = resolveByDestinationLabel(normalizedDestination);
                return new DestinationResolution(fallbackMatch, "name-based-fallback", true, false, true);
            }
            throw new ResourceNotFoundException("Unknown destinationAmenityId: " + normalizedId + " and destination fallback was not provided");
        }

        Amenity nameMatch = resolveByDestinationLabel(normalizedDestination);
        return new DestinationResolution(nameMatch, "name-based", false, false, false);
    }

    private Amenity resolveByDestinationLabel(String destinationLabel) {
        if (destinationLabel == null || destinationLabel.isBlank()) {
            throw new ResourceNotFoundException("No destination name provided for routing fallback");
        }

        String normalized = destinationLabel.toLowerCase(Locale.ENGLISH);
        if (normalized.contains("accessible")) {
            return firstByType(AmenityType.ACCESSIBLE_RESTROOM, destinationLabel);
        }
        if (normalized.contains("women")) {
            return firstByType(AmenityType.WOMEN_RESTROOM, destinationLabel);
        }
        if (normalized.contains("men")) {
            return firstByType(AmenityType.MEN_RESTROOM, destinationLabel);
        }

        return geoJsonAmenityLoader.getAmenities().stream()
                .min(Comparator.comparing(Amenity::getId))
                .orElseThrow(() -> new ResourceNotFoundException("No amenity found for destination: " + destinationLabel));
    }

    private static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Amenity firstByType(AmenityType amenityType, String destinationLabel) {
        return geoJsonAmenityLoader.getAmenities().stream()
                .filter(amenity -> amenity.getAmenityType() == amenityType)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No amenity found for destination: " + destinationLabel));
    }

    private Amenity resolveAmenityById(String amenityId) {
        String normalized = normalize(amenityId);
        if (normalized == null) {
            throw new BadRequestException("amenityId is required");
        }
        return geoJsonAmenityLoader.getAmenities().stream()
                .filter(amenity -> amenity.getId().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown amenityId: " + normalized));
    }

    private static double compositeRankingScore(AmenityDistance ad) {
        // Distance is the primary factor (meters/10 → 0-50 pt range for a 500m terminal).
        // Live status secondary factors are capped at ~10 pts combined (~100m equivalent),
        // so distance dominates beyond ~100m apart and live status tips close calls.
        double distanceScore = (ad.distanceMeters() != null ? ad.distanceMeters() : 9999.0) / 10.0;
        double waitScore = ad.liveStatus().waitTimeMinutes() * 0.3;
        double stallBonus = ad.liveStatus().stallsAvailable() * 0.15;
        double occupancyScore = occupancyOrdinal(ad.liveStatus().occupancyStatus()) * 0.8;
        return distanceScore + waitScore - stallBonus + occupancyScore;
    }

    private static int occupancyOrdinal(OccupancyStatus status) {
        return switch (status) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case FULL -> 3;
        };
    }

    private static LiveStatus mapLiveStatus(AmenityLiveStatus liveStatus) {
        return new LiveStatus(
                liveStatus.status(),
                liveStatus.statusReason(),
                Math.max(0, liveStatus.waitTimeMinutes()),
                Math.max(0, liveStatus.stallsAvailable()),
                liveStatus.occupancyStatus()
        );
    }

    private static ClosureTriggerState createClosureTriggerState(
            String sessionSeed,
            String amenityId,
            DemoSegmentMode mode
    ) {
        int hash = Math.floorMod(Objects.hash(sessionSeed, amenityId), Integer.MAX_VALUE);

        if (mode == DemoSegmentMode.SEGMENT_3) {
            int span = SEGMENT_EVENT_MAX_POLL - SEGMENT_EVENT_MIN_POLL + 1;
            int triggerPoll = SEGMENT_EVENT_MIN_POLL + (hash % span);
            ClosureSource source = ClosureSource.values()[(hash / 3) % ClosureSource.values().length];
            return new ClosureTriggerState(triggerPoll, source);
        }

        int phase = hash % 3;

        int min;
        int max;
        if (phase == 0) {
            min = EARLY_MIN_POLL;
            max = EARLY_MAX_POLL;
        } else if (phase == 1) {
            min = MID_MIN_POLL;
            max = MID_MAX_POLL;
        } else {
            min = LATE_MIN_POLL;
            max = LATE_MAX_POLL;
        }

        int span = max - min + 1;
        int triggerPoll = min + (hash % span);

        // Assign which source fires the closure: 0=ADMIN, 1=STAFF, 2=OCCUPANCY.
        ClosureSource source = ClosureSource.values()[(hash / 3) % ClosureSource.values().length];

        return new ClosureTriggerState(triggerPoll, source);
    }

    private enum ClosureSource {
        ADMIN, STAFF, OCCUPANCY
    }

    private static final class ClosureTriggerState {
        private final int triggerPoll;
        private final ClosureSource source;
        private int pollCount;
        private boolean delivered;

        private ClosureTriggerState(int triggerPoll, ClosureSource source) {
            this.triggerPoll = triggerPoll;
            this.source = source;
            this.pollCount = 0;
            this.delivered = false;
        }
    }

    public record DestinationResolution(
            Amenity amenity,
            String resolutionPath,
            boolean idProvided,
            boolean idMatched,
            boolean fallbackUsed
    ) {
    }

    public record AmenityDistance(Amenity amenity, Double distanceMeters, LiveStatus liveStatus) {
    }

    public record LiveStatus(
            AmenityStatus status,
            AmenityStatusReason statusReason,
            int waitTimeMinutes,
            int stallsAvailable,
            OccupancyStatus occupancyStatus
    ) {
        public boolean isSelectable() {
            return status == AmenityStatus.OPEN;
        }
    }

    public record AmenityRecommendation(Amenity amenity, Double distanceMeters, LiveStatus liveStatus) {
    }

    public record SessionResetResult(
            String sessionSeed,
            int clearedClosureTriggerCount
    ) {
    }
}
