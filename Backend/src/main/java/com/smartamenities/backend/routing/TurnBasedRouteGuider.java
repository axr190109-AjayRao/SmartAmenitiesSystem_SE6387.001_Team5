package com.smartamenities.backend.routing;

import com.smartamenities.backend.model.RouteGeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Derives turn-by-turn guidance from the rendered route polyline.
 */
public final class TurnBasedRouteGuider {

    private static final Logger log = LoggerFactory.getLogger(TurnBasedRouteGuider.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MIN_POINT_SEPARATION_METERS = 0.75;
    private static final double MIN_SEGMENT_METERS = 1.50;
    private static final double MICRO_TURN_MERGE_METERS = 4.00;
    private static final double STRAIGHT_THRESHOLD = 15.0;
    private static final double SLIGHT_TURN_THRESHOLD = 40.0;
    private static final double NORMAL_TURN_THRESHOLD = 140.0;
    private static final double DISTANCE_TOLERANCE_METERS = 0.001;

    private TurnBasedRouteGuider() {
    }

    public static List<String> generateTurnGuides(
            List<RouteGeoPoint> routeGeoPoints,
            double totalDistanceMeters,
            double destinationConnectorMeters,
            boolean accessibilityOn,
            String destinationName
    ) {
        return buildGuidancePlan(
                routeGeoPoints,
                totalDistanceMeters,
                destinationConnectorMeters,
                accessibilityOn,
                "the starting point",
                destinationName
        ).routeSteps();
    }

    public static List<String> generateTurnGuides(
            List<RouteGeoPoint> routeGeoPoints,
            double totalDistanceMeters,
            double destinationConnectorMeters,
            boolean accessibilityOn,
            String currentLocation,
            String destinationName
    ) {
        return buildGuidancePlan(
                routeGeoPoints,
                totalDistanceMeters,
                destinationConnectorMeters,
                accessibilityOn,
                currentLocation,
                destinationName
        ).routeSteps();
    }

    public static RouteGuidancePlan buildGuidancePlan(
            List<RouteGeoPoint> routeGeoPoints,
            double totalDistanceMeters,
            double destinationConnectorMeters,
            boolean accessibilityOn,
            String currentLocation,
            String destinationName
    ) {
        List<RouteGeoPoint> sanitizedPoints = sanitizePoints(routeGeoPoints);
        if (sanitizedPoints.size() < 2) {
            String fallbackInstruction = buildFallbackInstruction(destinationName);
            return new RouteGuidancePlan(
                    0.0,
                    List.of(fallbackInstruction),
                    List.of(),
                    List.of(new RouteStepProgressRange(0, 0.0, 0.0, fallbackInstruction))
            );
        }

        List<RouteSegment> routeSegments = buildSegments(sanitizedPoints);
        if (routeSegments.isEmpty()) {
            String fallbackInstruction = buildFallbackInstruction(destinationName);
            return new RouteGuidancePlan(
                    0.0,
                    List.of(fallbackInstruction),
                    List.of(),
                    List.of(new RouteStepProgressRange(0, 0.0, 0.0, fallbackInstruction))
            );
        }

        List<RouteLeg> routeLegs = mergeMicroLegs(buildLegs(routeSegments));
        if (routeLegs.isEmpty()) {
            String fallbackInstruction = buildFallbackInstruction(destinationName);
            return new RouteGuidancePlan(
                    0.0,
                    List.of(fallbackInstruction),
                    List.of(),
                    List.of(new RouteStepProgressRange(0, 0.0, 0.0, fallbackInstruction))
            );
        }

        double computedMeters = routeSegments.stream().mapToDouble(RouteSegment::distanceMeters).sum();
        log.info(
                "turn.guides totalPoints={} sanitizedPoints={} segments={} legs={} computedMeters={} totalMeters={} connectorMeters={} accessibilityOn={}",
                routeGeoPoints == null ? 0 : routeGeoPoints.size(),
                sanitizedPoints.size(),
                routeSegments.size(),
                routeLegs.size(),
                Math.round(computedMeters),
                Math.round(totalDistanceMeters),
                Math.round(destinationConnectorMeters),
                accessibilityOn
        );

        List<String> steps = new ArrayList<>(routeLegs.size() + 1);
        List<RouteSegmentProgress> segmentProgress = new ArrayList<>(routeSegments.size());
        List<RouteStepProgressRange> stepProgressRanges = new ArrayList<>(routeLegs.size() + 1);

        double cumulativeMeters = 0.0;
        int segmentIndex = 0;
        for (int legIndex = 0; legIndex < routeLegs.size(); legIndex++) {
            RouteLeg leg = routeLegs.get(legIndex);
            String instruction = legIndex == 0
                    ? buildStartInstruction(currentLocation, leg.distanceMeters(), accessibilityOn)
                    : buildLegInstruction(leg.maneuverType(), leg.distanceMeters(), accessibilityOn);

            steps.add(instruction);
            double stepStartMeters = cumulativeMeters;
            double stepEndMeters = stepStartMeters + leg.distanceMeters();
            stepProgressRanges.add(new RouteStepProgressRange(legIndex, stepStartMeters, stepEndMeters, instruction));

            double segmentStartMeters = stepStartMeters;
            for (int segmentOffset = 0; segmentOffset < leg.segmentCount(); segmentOffset++) {
                RouteSegment routeSegment = routeSegments.get(segmentIndex);
                double segmentEndMeters = segmentStartMeters + routeSegment.distanceMeters();
                segmentProgress.add(new RouteSegmentProgress(
                        segmentIndex,
                        routeSegment.from().getLatitude(),
                        routeSegment.from().getLongitude(),
                        routeSegment.to().getLatitude(),
                        routeSegment.to().getLongitude(),
                        routeSegment.distanceMeters(),
                        segmentStartMeters,
                        segmentEndMeters,
                        legIndex
                ));
                segmentIndex++;
                segmentStartMeters = segmentEndMeters;
            }

            cumulativeMeters = stepEndMeters;
        }

        double normalizedTotalMeters = Math.max(0.0, cumulativeMeters);
        segmentProgress = normalizeSegments(segmentProgress, normalizedTotalMeters);
        stepProgressRanges = normalizeStepRanges(stepProgressRanges, normalizedTotalMeters);

        String arrivalInstruction = buildArrivalInstruction(destinationName);
        steps.add(arrivalInstruction);
        stepProgressRanges.add(new RouteStepProgressRange(routeLegs.size(), normalizedTotalMeters, normalizedTotalMeters, arrivalInstruction));

        log.debug(
                "turn.guides.plan totalDistanceMeters={} stepBoundaries={} segmentCount={}",
            Math.round(normalizedTotalMeters),
                stepProgressRanges.size(),
                segmentProgress.size()
        );

        return new RouteGuidancePlan(
            normalizedTotalMeters,
                Collections.unmodifiableList(steps),
                Collections.unmodifiableList(segmentProgress),
                Collections.unmodifiableList(stepProgressRanges)
        );
    }

        private static List<RouteSegmentProgress> normalizeSegments(
            List<RouteSegmentProgress> segmentProgress,
            double totalDistanceMeters
        ) {
        if (segmentProgress.isEmpty()) {
            return segmentProgress;
        }

        List<RouteSegmentProgress> normalized = new ArrayList<>(segmentProgress.size());
        double cursor = 0.0;
        for (int index = 0; index < segmentProgress.size(); index++) {
            RouteSegmentProgress segment = segmentProgress.get(index);
            double endMeters = (index == segmentProgress.size() - 1)
                ? totalDistanceMeters
                : Math.max(cursor, Math.min(totalDistanceMeters, segment.cumulativeEndMeters()));
            double lengthMeters = Math.max(0.0, endMeters - cursor);
            int normalizedStepIndex = segment.stepIndex();

            normalized.add(new RouteSegmentProgress(
                segment.segmentIndex(),
                segment.fromLat(),
                segment.fromLon(),
                segment.toLat(),
                segment.toLon(),
                lengthMeters,
                cursor,
                endMeters,
                normalizedStepIndex
            ));
            cursor = endMeters;
        }

        return normalized;
        }

        private static List<RouteStepProgressRange> normalizeStepRanges(
            List<RouteStepProgressRange> stepProgressRanges,
            double totalDistanceMeters
        ) {
        if (stepProgressRanges.isEmpty()) {
            return stepProgressRanges;
        }

        List<RouteStepProgressRange> normalized = new ArrayList<>(stepProgressRanges.size());
        double cursor = 0.0;
        for (int index = 0; index < stepProgressRanges.size(); index++) {
            RouteStepProgressRange range = stepProgressRanges.get(index);
            double endMeters = Math.max(cursor, Math.min(totalDistanceMeters, range.endMeters()));
            if (index == stepProgressRanges.size() - 1) {
            endMeters = totalDistanceMeters;
            }

            normalized.add(new RouteStepProgressRange(
                range.stepIndex(),
                cursor,
                endMeters,
                range.instruction()
            ));
            cursor = endMeters;
        }

        RouteStepProgressRange firstRange = normalized.get(0);
        if (Math.abs(firstRange.startMeters()) > DISTANCE_TOLERANCE_METERS) {
            normalized.set(
                0,
                new RouteStepProgressRange(
                    firstRange.stepIndex(),
                    0.0,
                    firstRange.endMeters(),
                    firstRange.instruction()
                )
            );
        }

        return normalized;
        }

    private static List<RouteGeoPoint> sanitizePoints(List<RouteGeoPoint> routeGeoPoints) {
        if (routeGeoPoints == null || routeGeoPoints.isEmpty()) {
            return List.of();
        }

        List<RouteGeoPoint> sanitized = new ArrayList<>();
        for (RouteGeoPoint point : routeGeoPoints) {
            if (point == null) {
                continue;
            }
            if (!sanitized.isEmpty()) {
                RouteGeoPoint previous = sanitized.get(sanitized.size() - 1);
                if (haversineDistance(previous, point) < MIN_POINT_SEPARATION_METERS) {
                    continue;
                }
            }
            sanitized.add(point);
        }
        return sanitized;
    }

    private static List<RouteSegment> buildSegments(List<RouteGeoPoint> points) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            RouteGeoPoint previous = points.get(index - 1);
            RouteGeoPoint current = points.get(index);
            double distanceMeters = haversineDistance(previous, current);
            if (distanceMeters < MIN_SEGMENT_METERS) {
                continue;
            }
            segments.add(new RouteSegment(previous, current, distanceMeters, computeHeading(previous, current)));
        }
        return segments;
    }

    private static List<RouteLeg> buildLegs(List<RouteSegment> segments) {
        if (segments.isEmpty()) {
            return List.of();
        }
        if (segments.size() == 1) {
            return List.of(new RouteLeg("straight", segments.get(0).distanceMeters(), 1));
        }

        List<RouteLeg> legs = new ArrayList<>();
        String currentManeuver = "straight";
        double currentDistance = segments.get(0).distanceMeters();
        int currentSegmentCount = 1;
        double previousHeading = segments.get(0).headingDegrees();
        double previousSegmentDistance = segments.get(0).distanceMeters();

        for (int index = 1; index < segments.size(); index++) {
            RouteSegment segment = segments.get(index);
            double angleDelta = normalizeAngleDelta(segment.headingDegrees() - previousHeading);

            if (isMicroNoise(angleDelta, previousSegmentDistance, segment.distanceMeters())) {
                currentDistance += segment.distanceMeters();
                currentSegmentCount++;
            } else {
                legs.add(new RouteLeg(currentManeuver, currentDistance, currentSegmentCount));
                currentManeuver = classifyManeuver(angleDelta);
                currentDistance = segment.distanceMeters();
                currentSegmentCount = 1;
            }

            previousHeading = segment.headingDegrees();
            previousSegmentDistance = segment.distanceMeters();
        }

        legs.add(new RouteLeg(currentManeuver, currentDistance, currentSegmentCount));
        return legs;
    }

    private static List<RouteLeg> mergeMicroLegs(List<RouteLeg> routeLegs) {
        if (routeLegs.size() < 2) {
            return routeLegs;
        }

        List<RouteLeg> merged = new ArrayList<>();
        for (RouteLeg leg : routeLegs) {
            if (merged.isEmpty()) {
                merged.add(leg);
                continue;
            }

            RouteLeg previous = merged.get(merged.size() - 1);
            if (!isStraight(leg.maneuverType()) && leg.distanceMeters() < MICRO_TURN_MERGE_METERS) {
                merged.set(
                        merged.size() - 1,
                        new RouteLeg(
                                strongerManeuver(previous.maneuverType(), leg.maneuverType()),
                        previous.distanceMeters() + leg.distanceMeters(),
                        previous.segmentCount() + leg.segmentCount()
                        )
                );
                continue;
            }

            if (isStraight(leg.maneuverType()) && leg.distanceMeters() < MIN_SEGMENT_METERS) {
                merged.set(
                        merged.size() - 1,
                    new RouteLeg(
                        previous.maneuverType(),
                        previous.distanceMeters() + leg.distanceMeters(),
                        previous.segmentCount() + leg.segmentCount()
                    )
                );
                continue;
            }

            merged.add(leg);
        }
        return merged;
    }

    private static boolean isMicroNoise(double angleDelta, double previousSegmentDistance, double currentSegmentDistance) {
        return Math.abs(angleDelta) < STRAIGHT_THRESHOLD
                || previousSegmentDistance < MIN_SEGMENT_METERS
                || currentSegmentDistance < MIN_SEGMENT_METERS;
    }

    private static boolean isStraight(String maneuverType) {
        return "straight".equals(maneuverType);
    }

    private static String strongerManeuver(String first, String second) {
        return maneuverRank(second) > maneuverRank(first) ? second : first;
    }

    private static int maneuverRank(String maneuverType) {
        if (maneuverType == null) {
            return 0;
        }
        return switch (maneuverType) {
            case "u-turn" -> 4;
            case "left", "right" -> 3;
            case "slight left", "slight right" -> 2;
            case "straight" -> 1;
            default -> 1;
        };
    }

    private static String buildStartInstruction(String currentLocation, double distanceMeters, boolean accessibilityOn) {
        String startLabel = normalizeStartLocation(currentLocation);
        String distanceLabel = formatDistance(distanceMeters);
        if (accessibilityOn) {
            return "From " + startLabel + ", follow the accessible corridor forward approximately " + distanceLabel + " meters.";
        }
        return "From " + startLabel + ", proceed forward approximately " + distanceLabel + " meters.";
    }

    private static String buildLegInstruction(String maneuverType, double distanceMeters, boolean accessibilityOn) {
        String distanceLabel = formatDistance(distanceMeters);
        String corridorSuffix = accessibilityOn ? " along the accessible corridor" : "";

        return switch (maneuverType) {
            case "straight" -> "Continue straight" + corridorSuffix + " for approximately " + distanceLabel + " meters.";
            case "slight left" -> "Slight left" + corridorSuffix + ", continue approximately " + distanceLabel + " meters.";
            case "slight right" -> "Slight right" + corridorSuffix + ", continue approximately " + distanceLabel + " meters.";
            case "left" -> "Turn left" + corridorSuffix + " and continue for approximately " + distanceLabel + " meters.";
            case "right" -> "Turn right" + corridorSuffix + " and continue for approximately " + distanceLabel + " meters.";
            case "u-turn" -> "Make a U-turn" + corridorSuffix + " and continue for approximately " + distanceLabel + " meters.";
            default -> "Continue forward for approximately " + distanceLabel + " meters.";
        };
    }

    private static String buildArrivalInstruction(String destinationName) {
        String label = destinationName == null || destinationName.isBlank() ? "the destination" : destinationName.trim();
        return "Arrive at " + label + ".";
    }

    private static String buildFallbackInstruction(String destinationName) {
        String label = destinationName == null || destinationName.isBlank() ? "the destination" : destinationName.trim();
        return "Proceed toward " + label + ".";
    }

    private static String normalizeStartLocation(String currentLocation) {
        if (currentLocation == null || currentLocation.isBlank()) {
            return "the starting point";
        }
        return currentLocation.trim();
    }

    private static String classifyManeuver(double angleDelta) {
        double normalized = Math.abs(angleDelta);
        if (normalized >= NORMAL_TURN_THRESHOLD) {
            return "u-turn";
        }
        if (normalized >= SLIGHT_TURN_THRESHOLD) {
            return angleDelta > 0 ? "right" : "left";
        }
        if (normalized >= STRAIGHT_THRESHOLD) {
            return angleDelta > 0 ? "slight right" : "slight left";
        }
        return "straight";
    }

    private static double computeHeading(RouteGeoPoint from, RouteGeoPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    private static double normalizeAngleDelta(double delta) {
        delta = delta % 360.0;
        if (delta > 180.0) {
            delta -= 360.0;
        } else if (delta < -180.0) {
            delta += 360.0;
        }
        return delta;
    }

    private static double haversineDistance(RouteGeoPoint p1, RouteGeoPoint p2) {
        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static String formatDistance(double meters) {
        int rounded = Math.max(1, (int) Math.round(meters));
        return String.valueOf(rounded);
    }

    private record RouteSegment(RouteGeoPoint from, RouteGeoPoint to, double distanceMeters, double headingDegrees) {
    }

    private record RouteLeg(String maneuverType, double distanceMeters, int segmentCount) {
    }
}
