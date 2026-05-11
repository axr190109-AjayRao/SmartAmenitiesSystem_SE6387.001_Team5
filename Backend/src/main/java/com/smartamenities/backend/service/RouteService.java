package com.smartamenities.backend.service;

import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.integration.wayfinder.ResolvedWayfinderRequest;
import com.smartamenities.backend.integration.wayfinder.SimulatedWayfinderProvider;
import com.smartamenities.backend.integration.wayfinder.WayfinderProvider;
import com.smartamenities.backend.exception.ResourceNotFoundException;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.DemoSegmentMode;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.routing.RouteGuidancePlan;
import com.smartamenities.backend.routing.RouteGenerator;
import com.smartamenities.backend.routing.RouteNetworkRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for route generation coordination.
 * Acts as the application layer between controller and routing/domain logic.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private static final String DEFAULT_CURRENT_LOCATION = "Terminal D Departures";

    private final AmenityService amenityService;
    private final GeoJsonAmenityLoader geoJsonAmenityLoader;
    private final RouteMetricsService routeMetricsService;
    private final WayfinderProvider wayfinderProvider;
    private final DemoSessionConfigService demoSessionConfigService;

    @Autowired
    public RouteService(
            AmenityService amenityService,
            GeoJsonAmenityLoader geoJsonAmenityLoader,
            RouteMetricsService routeMetricsService,
            WayfinderProvider wayfinderProvider,
            DemoSessionConfigService demoSessionConfigService
    ) {
        this.amenityService = amenityService;
        this.geoJsonAmenityLoader = geoJsonAmenityLoader;
        this.routeMetricsService = routeMetricsService;
        this.wayfinderProvider = wayfinderProvider;
        this.demoSessionConfigService = demoSessionConfigService;
    }

    public RouteService(
            AmenityService amenityService,
            GeoJsonAmenityLoader geoJsonAmenityLoader,
            RouteMetricsService routeMetricsService
    ) {
        this(
                amenityService,
                geoJsonAmenityLoader,
                routeMetricsService,
                new SimulatedWayfinderProvider(),
                new DemoSessionConfigService()
        );
    }

    /**
     * Generate a route based on the given request.
     *
     * @param request contains destination, currentLocation, and accessibilityOn preference
     * @return calculated Route
     */
    public Route generateRoute(RouteRequest request) {
        return generateRoute(request, null);
    }

    public Route generateRoute(RouteRequest request, String sessionSeed) {
        ResolvedWayfinderRequest resolvedRequest = wayfinderProvider.resolveRequest(
                request.getDestinationAmenityId(),
                request.getDestination(),
                request.getCurrentLocation(),
                DEFAULT_CURRENT_LOCATION
        );

        AmenityService.DestinationResolution resolution = amenityService.resolveDestinationForRoute(
            resolvedRequest.destinationAmenityId(),
            resolvedRequest.destinationLabel()
        );
        Amenity destinationAmenity = resolution.amenity();
        boolean hasExplicitCurrentCoordinates = request.getCurrentLatitude() != null && request.getCurrentLongitude() != null;
        applySegmentRerouteGating(sessionSeed, destinationAmenity.getId(), hasExplicitCurrentCoordinates);

        log.info(
            "route.lookup path={} idProvided={} idMatched={} fallbackUsed={} destinationAmenityId={} destinationLabel={} resolvedAmenityId={}",
            resolution.resolutionPath(),
            resolution.idProvided(),
            resolution.idMatched(),
            resolution.fallbackUsed(),
            resolvedRequest.destinationAmenityId(),
            resolvedRequest.destinationLabel(),
            destinationAmenity.getId()
        );

        RouteNetworkRouter.PathResult pathResult;
        RouteMetricsService.RouteComputation routeComputation;
        try {
                routeComputation = routeMetricsService.computeRouteMetrics(
                    destinationAmenity,
                    request.isAccessibilityOn(),
                    request.getCurrentLatitude(),
                    request.getCurrentLongitude(),
                    sessionSeed
                );
            pathResult = routeComputation.pathResult();
        } catch (IllegalStateException ex) {
            throw new ResourceNotFoundException("No walkable path found for the selected destination");
        }

        RouteGeoPoint requestedStartAnchor = routeComputation.requestedStartAnchor();

        log.debug(
            "route.path.snap originalStartLat={} originalStartLon={} snappedStartLat={} snappedStartLon={} startSnapDistanceMeters={} snappedDestLat={} snappedDestLon={} destSnapDistanceMeters={}",
            requestedStartAnchor.getLatitude(),
            requestedStartAnchor.getLongitude(),
            pathResult.snappedStart().getLatitude(),
            pathResult.snappedStart().getLongitude(),
            pathResult.startSnapDistanceMeters(),
            pathResult.snappedDestination().getLatitude(),
            pathResult.snappedDestination().getLongitude(),
            pathResult.destinationSnapDistanceMeters()
        );

        List<RouteGeoPoint> routeGeoPoints = pathResult.routeGeoPoints();

        // When routing from explicit GPS coordinates (reroute case), the network router
        // snaps the anchor to the nearest walkable edge. Prepend the exact passenger
        // position so the polyline begins precisely where they are, not at the snapped node.
        double extraStartMeters = 0.0;
        if (hasExplicitCurrentCoordinates && pathResult.startSnapDistanceMeters() > 0.5) {
            RouteGeoPoint exactStart = routeComputation.requestedStartAnchor();
            List<RouteGeoPoint> withExactStart = new ArrayList<>(routeGeoPoints.size() + 1);
            withExactStart.add(exactStart);
            withExactStart.addAll(routeGeoPoints);
            routeGeoPoints = withExactStart;
            extraStartMeters = pathResult.startSnapDistanceMeters();
        }

        RouteGeoPoint firstRoutePoint = routeGeoPoints.isEmpty() ? null : routeGeoPoints.get(0);
        RouteGeoPoint lastRoutePoint = routeGeoPoints.isEmpty() ? null : routeGeoPoints.get(routeGeoPoints.size() - 1);
        String routeStartLocationName = sessionSeed != null && !sessionSeed.isBlank()
            ? routeComputation.startLocation().getName()
            : resolvedRequest.currentLocation();
        RouteGeoPoint responseStartPoint = (sessionSeed != null && !sessionSeed.isBlank()) || hasExplicitCurrentCoordinates
            ? routeComputation.requestedStartAnchor()
            : pathResult.snappedStart();
        RouteGuidancePlan guidancePlan = RouteGenerator.generateRouteGuidancePlan(
            routeStartLocationName,
                destinationAmenity,
                request.isAccessibilityOn(),
                routeGeoPoints,
                routeComputation.routeDistanceMeters() + extraStartMeters,
                pathResult.connectorDistanceMeters()
        );
        double routeDistanceMeters = guidancePlan.totalDistanceMeters();

        log.debug(
            "route.path.stats startNodes=({}, {}) destinationNodes=({}, {}) pathNodeCount={} totalDistanceMeters={} firstRoutePoint={} lastRoutePoint={} stepBoundaries={}",
            pathResult.startNodeA(),
            pathResult.startNodeB(),
            pathResult.destinationNodeA(),
            pathResult.destinationNodeB(),
            pathResult.pathNodeCount(),
            routeDistanceMeters,
            firstRoutePoint,
            lastRoutePoint,
            guidancePlan.stepProgressRanges().size()
        );

        List<String> routeSteps = guidancePlan.routeSteps();

        // Rough indoor walking speed baseline: 75 meters/minute
        int estimatedMinutes = Math.max(1, (int) Math.ceil(routeDistanceMeters / 75.0));
        String estimatedTime = estimatedMinutes + " min";

        // Build and return the route
        Route route = new Route(
                destinationAmenity.getDisplayName(),
            routeStartLocationName,
            sessionSeed != null && !sessionSeed.isBlank()
                ? routeComputation.startLocation().getName()
                : geoJsonAmenityLoader.getStartLocation().getName(),
                request.isAccessibilityOn(),
                estimatedTime,
                routeSteps,
            responseStartPoint.getLatitude(),
            responseStartPoint.getLongitude(),
                destinationAmenity.getLatitude(),
                destinationAmenity.getLongitude(),
                routeGeoPoints,
                routeDistanceMeters,
                guidancePlan.routeSegments(),
                guidancePlan.stepProgressRanges()
        );

        log.info(
                "route.response destination={} stepsCount={} estimatedTime={} startLat={} startLon={} destLat={} destLon={} totalDistanceMeters={} routeSegments={} stepRanges={}",
                route.getDestination(),
                route.getRouteSteps() == null ? 0 : route.getRouteSteps().size(),
                route.getEstimatedTime(),
                route.getStartLatitude(),
                route.getStartLongitude(),
                route.getDestinationLatitude(),
                route.getDestinationLongitude(),
                route.getTotalDistanceMeters(),
                route.getRouteSegments() == null ? 0 : route.getRouteSegments().size(),
                route.getStepProgressRanges() == null ? 0 : route.getStepProgressRanges().size()
        );

        return route;
    }

    private void applySegmentRerouteGating(
            String sessionSeed,
            String resolvedDestinationAmenityId,
            boolean hasExplicitCurrentCoordinates
    ) {
        if (sessionSeed == null || sessionSeed.isBlank()) {
            return;
        }

        DemoSegmentMode mode = demoSessionConfigService.resolveMode(sessionSeed);
        if (!hasExplicitCurrentCoordinates) {
            demoSessionConfigService.recordInitialDestination(sessionSeed, resolvedDestinationAmenityId);
            return;
        }

        if (mode == DemoSegmentMode.LEGACY || mode == DemoSegmentMode.SEGMENT_4) {
            return;
        }

        if (mode == DemoSegmentMode.SEGMENT_2 || mode == DemoSegmentMode.SEGMENT_3) {
            String initialDestination = demoSessionConfigService.getInitialDestination(sessionSeed);
            if (initialDestination != null && !initialDestination.equals(resolvedDestinationAmenityId)) {
                return;
            }
        }

        throw new BadRequestException("Current segment does not allow passenger-deviation reroute behavior");
    }

}
