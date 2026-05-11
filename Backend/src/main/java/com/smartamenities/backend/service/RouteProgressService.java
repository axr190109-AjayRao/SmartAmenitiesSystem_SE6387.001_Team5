package com.smartamenities.backend.service;

import com.smartamenities.backend.dto.RouteGeoPoint;
import com.smartamenities.backend.dto.RouteProgressRequest;
import com.smartamenities.backend.dto.RouteProgressResponse;
import com.smartamenities.backend.dto.RouteStepProgressRangeResponse;
import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.routing.GeoMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateless progress simulator for geometry returned by POST /route.
 */
@Service
public class RouteProgressService {

    private static final Logger log = LoggerFactory.getLogger(RouteProgressService.class);
    private static final double ARRIVAL_EPSILON_METERS = 0.50;
    private static final double OFF_ROUTE_SPATIAL_THRESHOLD_METERS = 25.0;
    private static final double OFF_ROUTE_DIRECTIONAL_THRESHOLD_METERS = 25.0;
    private static final double BACKTRACK_PROGRESS_THRESHOLD_METERS = 20.0;
    private static final double BACKTRACK_EXPECTED_DELTA_THRESHOLD_METERS = 15.0;

    public RouteProgressResponse simulateProgress(RouteProgressRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.getRouteGeoPoints() == null || request.getRouteGeoPoints().size() < 2) {
            throw new BadRequestException("routeGeoPoints must contain at least two points");
        }
        if (request.getStepProgressRanges() == null || request.getStepProgressRanges().isEmpty()) {
            throw new BadRequestException("stepProgressRanges must contain at least one range");
        }
        if (request.getProgressMeters() == null) {
            throw new BadRequestException("progressMeters is required");
        }

        double totalDistanceMeters = resolveTotalDistance(request);
        double clampedProgressMeters = clamp(request.getProgressMeters(), 0.0, totalDistanceMeters);
        List<RouteStepProgressRangeResponse> stepProgressRanges = request.getStepProgressRanges();
        int lastStepIndex = stepProgressRanges.size() - 1;
        double remainingDistanceMeters = Math.max(0.0, totalDistanceMeters - clampedProgressMeters);
        boolean arrived = clampedProgressMeters >= (totalDistanceMeters - ARRIVAL_EPSILON_METERS)
                || remainingDistanceMeters <= ARRIVAL_EPSILON_METERS;

        int activeStepIndex = resolveActiveStepIndex(stepProgressRanges, clampedProgressMeters);
        if (arrived) {
            activeStepIndex = lastStepIndex;
            remainingDistanceMeters = 0.0;
        }

        RouteStepProgressRangeResponse activeRange = stepProgressRanges.get(activeStepIndex);
        RouteGeoPoint snappedPoint = interpolateAlongPolyline(request.getRouteGeoPoints(), clampedProgressMeters, totalDistanceMeters);

        double deviationMeters = 0.0;
        double distanceFromExpectedPosition = 0.0;
        double backtrackMeters = 0.0;
        boolean isOffRoute = false;
        if (request.getActualLatitude() != null && request.getActualLongitude() != null) {
            PolylineProjection projection = closestProjectionOnPolyline(
                    request.getActualLatitude(), request.getActualLongitude(),
                    request.getRouteGeoPoints()
            );
            deviationMeters = projection.distanceMeters();
            // Directional check: how far is the user from where the simulation expects them?
            // A user walking at a different speed stays near the polyline (spatial check fails).
            // A user who missed a turn fails both: they're off the polyline AND far from
            // the expected position, because they've continued in the pre-turn direction.
            distanceFromExpectedPosition = GeoMath.haversineMeters(
                    request.getActualLatitude(), request.getActualLongitude(),
                    snappedPoint.getLatitude(), snappedPoint.getLongitude()
            );
            backtrackMeters = Math.max(0.0, clampedProgressMeters - projection.progressMeters());

            boolean wrongTurnDeviation = deviationMeters > OFF_ROUTE_SPATIAL_THRESHOLD_METERS
                    && distanceFromExpectedPosition > OFF_ROUTE_DIRECTIONAL_THRESHOLD_METERS;
            boolean wrongWayBacktracking = backtrackMeters > BACKTRACK_PROGRESS_THRESHOLD_METERS
                    && distanceFromExpectedPosition > BACKTRACK_EXPECTED_DELTA_THRESHOLD_METERS;

            isOffRoute = wrongTurnDeviation || wrongWayBacktracking;
        }

        List<String> remainingSteps = new ArrayList<>();
        for (int index = activeStepIndex; index < stepProgressRanges.size(); index++) {
            remainingSteps.add(stepProgressRanges.get(index).getInstruction());
        }

        log.debug(
                "route.progress progressMeters={} totalDistanceMeters={} clampedProgressMeters={} remainingDistanceMeters={} resolvedIndex={} lastIndex={} epsilonMeters={} arrived={} activeInstruction={} deviationMeters={} expectedDeltaMeters={} backtrackMeters={} isOffRoute={}",
                request.getProgressMeters(),
                Math.round(totalDistanceMeters),
                Math.round(clampedProgressMeters),
                Math.round(remainingDistanceMeters),
                activeStepIndex,
                lastStepIndex,
                ARRIVAL_EPSILON_METERS,
                arrived,
                activeRange.getInstruction(),
                Math.round(deviationMeters),
                Math.round(distanceFromExpectedPosition),
                Math.round(backtrackMeters),
                isOffRoute
        );

        return new RouteProgressResponse(
                snappedPoint.getLatitude(),
                snappedPoint.getLongitude(),
                clampedProgressMeters,
                activeStepIndex,
                activeRange.getInstruction(),
                remainingDistanceMeters,
                Collections.unmodifiableList(remainingSteps),
                arrived,
                isOffRoute,
                deviationMeters
        );
    }

    private static double resolveTotalDistance(RouteProgressRequest request) {
        if (request.getTotalDistanceMeters() != null && request.getTotalDistanceMeters() > 0.0) {
            return request.getTotalDistanceMeters();
        }

        List<RouteStepProgressRangeResponse> ranges = request.getStepProgressRanges();
        RouteStepProgressRangeResponse lastRange = ranges.get(ranges.size() - 1);
        if (lastRange.getEndMeters() > 0.0) {
            return lastRange.getEndMeters();
        }

        double total = 0.0;
        List<RouteGeoPoint> points = request.getRouteGeoPoints();
        for (int index = 1; index < points.size(); index++) {
            RouteGeoPoint previous = points.get(index - 1);
            RouteGeoPoint current = points.get(index);
            total += GeoMath.haversineMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude()
            );
        }
        return total;
    }

    private static int resolveActiveStepIndex(List<RouteStepProgressRangeResponse> stepProgressRanges, double progressMeters) {
        for (int index = 0; index < stepProgressRanges.size(); index++) {
            RouteStepProgressRangeResponse range = stepProgressRanges.get(index);
            boolean isLast = index == stepProgressRanges.size() - 1;
            if (progressMeters < range.getEndMeters() || (isLast && progressMeters <= range.getEndMeters())) {
                return index;
            }
        }
        return stepProgressRanges.size() - 1;
    }

    private static RouteGeoPoint interpolateAlongPolyline(List<RouteGeoPoint> routeGeoPoints, double progressMeters, double totalDistanceMeters) {
        if (progressMeters <= 0.0) {
            RouteGeoPoint first = routeGeoPoints.get(0);
            return new RouteGeoPoint(first.getLatitude(), first.getLongitude());
        }
        if (progressMeters >= totalDistanceMeters) {
            RouteGeoPoint last = routeGeoPoints.get(routeGeoPoints.size() - 1);
            return new RouteGeoPoint(last.getLatitude(), last.getLongitude());
        }

        double accumulatedMeters = 0.0;
        for (int index = 1; index < routeGeoPoints.size(); index++) {
            RouteGeoPoint previous = routeGeoPoints.get(index - 1);
            RouteGeoPoint current = routeGeoPoints.get(index);
            double segmentMeters = GeoMath.haversineMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude()
            );
            if (segmentMeters <= 0.0) {
                continue;
            }

            double segmentEndMeters = accumulatedMeters + segmentMeters;
            if (progressMeters <= segmentEndMeters) {
                double ratio = (progressMeters - accumulatedMeters) / segmentMeters;
                double latitude = previous.getLatitude() + ((current.getLatitude() - previous.getLatitude()) * ratio);
                double longitude = previous.getLongitude() + ((current.getLongitude() - previous.getLongitude()) * ratio);
                return new RouteGeoPoint(latitude, longitude);
            }

            accumulatedMeters = segmentEndMeters;
        }

        RouteGeoPoint last = routeGeoPoints.get(routeGeoPoints.size() - 1);
        return new RouteGeoPoint(last.getLatitude(), last.getLongitude());
    }

    private static PolylineProjection closestProjectionOnPolyline(double actualLat, double actualLon, List<RouteGeoPoint> points) {
        double minDistance = Double.MAX_VALUE;
        double bestProgressMeters = 0.0;
        double accumulatedMeters = 0.0;
        for (int index = 1; index < points.size(); index++) {
            RouteGeoPoint a = points.get(index - 1);
            RouteGeoPoint b = points.get(index);
            double abLat = b.getLatitude() - a.getLatitude();
            double abLon = b.getLongitude() - a.getLongitude();
            double abLenSq = abLat * abLat + abLon * abLon;
            double segmentMeters = GeoMath.haversineMeters(
                    a.getLatitude(),
                    a.getLongitude(),
                    b.getLatitude(),
                    b.getLongitude()
            );
            if (abLenSq <= 0.0) {
                double distance = GeoMath.haversineMeters(actualLat, actualLon, a.getLatitude(), a.getLongitude());
                if (distance < minDistance) {
                    minDistance = distance;
                    bestProgressMeters = accumulatedMeters;
                }
                accumulatedMeters += segmentMeters;
                continue;
            }
            double t = Math.max(0.0, Math.min(1.0,
                    ((actualLat - a.getLatitude()) * abLat + (actualLon - a.getLongitude()) * abLon) / abLenSq));
            double closestLat = a.getLatitude() + t * abLat;
            double closestLon = a.getLongitude() + t * abLon;
            double distance = GeoMath.haversineMeters(actualLat, actualLon, closestLat, closestLon);
            if (distance < minDistance) {
                minDistance = distance;
                bestProgressMeters = accumulatedMeters + (segmentMeters * t);
            }
            accumulatedMeters += segmentMeters;
        }
        if (minDistance == Double.MAX_VALUE) {
            return new PolylineProjection(0.0, 0.0);
        }
        return new PolylineProjection(minDistance, bestProgressMeters);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PolylineProjection(double distanceMeters, double progressMeters) {}
}
