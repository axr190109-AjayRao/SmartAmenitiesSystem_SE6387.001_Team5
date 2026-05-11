package com.smartamenities.backend.controller;

import com.smartamenities.backend.dto.RerouteRequest;
import com.smartamenities.backend.dto.RouteGeoPoint;
import com.smartamenities.backend.dto.RouteProgressRequest;
import com.smartamenities.backend.dto.RouteProgressResponse;
import com.smartamenities.backend.dto.RouteSegmentResponse;
import com.smartamenities.backend.dto.RouteRequest;
import com.smartamenities.backend.dto.RouteResponse;
import com.smartamenities.backend.dto.RouteStepResponse;
import com.smartamenities.backend.dto.RouteStepProgressRangeResponse;
import com.smartamenities.backend.exception.BadRequestException;
import com.smartamenities.backend.model.Route;
import com.smartamenities.backend.service.RouteService;
import com.smartamenities.backend.service.RouteProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/route")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteService routeService;
    private final RouteProgressService routeProgressService;

    public RouteController(RouteService routeService, RouteProgressService routeProgressService) {
        this.routeService = routeService;
        this.routeProgressService = routeProgressService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> createRoute(
            @RequestParam(required = false) String sessionSeed,
            @RequestBody RouteRequest routeRequest
    ) {
        if (routeRequest == null) {
            throw new BadRequestException("Request body is required");
        }

        log.info(
            "route.request destination={} currentLocation={} accessibilityOn={} destinationAmenityId={}",
            routeRequest.getDestination(),
            routeRequest.getCurrentLocation(),
            routeRequest.isAccessibilityOn(),
            routeRequest.getDestinationAmenityId()
        );

        Route route = routeService.generateRoute(routeRequest, sessionSeed);
        RouteResponse response = toRouteResponse(route);

        log.info(
            "route.controller.response destination={} stepsCount={}",
            response.getDestination(),
            response.getRouteSteps() == null ? 0 : response.getRouteSteps().size()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reroute")
    public ResponseEntity<RouteResponse> reroute(
            @RequestParam(required = false) String sessionSeed,
            @RequestBody RerouteRequest rerouteRequest
    ) {
        if (rerouteRequest == null) {
            throw new BadRequestException("Request body is required");
        }
        if (rerouteRequest.getCurrentLatitude() == null || rerouteRequest.getCurrentLongitude() == null) {
            throw new BadRequestException("currentLatitude and currentLongitude are required for rerouting");
        }
        if (rerouteRequest.getDestinationAmenityId() == null || rerouteRequest.getDestinationAmenityId().isBlank()) {
            throw new BadRequestException("destinationAmenityId is required for rerouting");
        }

        log.info(
            "reroute.request lat={} lon={} destinationAmenityId={} accessibilityOn={} avoidSegments={}",
            rerouteRequest.getCurrentLatitude(),
            rerouteRequest.getCurrentLongitude(),
            rerouteRequest.getDestinationAmenityId(),
            rerouteRequest.isAccessibilityOn(),
            rerouteRequest.getAvoidSegments()
        );

        RouteRequest routeRequest = new RouteRequest(
            rerouteRequest.getDestinationAmenityId(),
            rerouteRequest.getCurrentLatitude() + "," + rerouteRequest.getCurrentLongitude(),
            rerouteRequest.isAccessibilityOn(),
            rerouteRequest.getDestinationAmenityId(),
            rerouteRequest.getCurrentLatitude(),
            rerouteRequest.getCurrentLongitude()
        );

        return ResponseEntity.ok(toRouteResponse(routeService.generateRoute(routeRequest, sessionSeed)));
    }

    @PostMapping("/simulate-progress")
    public ResponseEntity<RouteProgressResponse> simulateProgress(@RequestBody RouteProgressRequest request) {
        return ResponseEntity.ok(routeProgressService.simulateProgress(request));
    }

    @PostMapping("/progress-from-geometry")
    public ResponseEntity<RouteProgressResponse> progressFromGeometry(@RequestBody RouteProgressRequest request) {
        return ResponseEntity.ok(routeProgressService.simulateProgress(request));
    }

    private static RouteResponse toRouteResponse(Route route) {
        List<RouteStepResponse> steps = route.getRouteSteps() == null
            ? Collections.emptyList()
            : route.getRouteSteps().stream().map(RouteStepResponse::new).toList();

        return new RouteResponse(
            route.getDestination(),
            route.isAccessibilityOn(),
            route.getEstimatedTime(),
            steps,
            route.getStartLocationName(),
            route.getStartLatitude(),
            route.getStartLongitude(),
            route.getDestinationLatitude(),
            route.getDestinationLongitude(),
            route.getRouteGeoPoints() == null ? null : route.getRouteGeoPoints().stream()
                .map(p -> new RouteGeoPoint(p.getLatitude(), p.getLongitude()))
                .toList(),
            route.getTotalDistanceMeters(),
            route.getRouteSegments() == null ? null : route.getRouteSegments().stream()
                .map(s -> new RouteSegmentResponse(
                    s.segmentIndex(), s.fromLat(), s.fromLon(), s.toLat(), s.toLon(),
                    s.lengthMeters(), s.cumulativeStartMeters(), s.cumulativeEndMeters(), s.stepIndex()
                ))
                .toList(),
            route.getStepProgressRanges() == null ? null : route.getStepProgressRanges().stream()
                .map(r -> new RouteStepProgressRangeResponse(
                    r.stepIndex(), r.startMeters(), r.endMeters(), r.instruction()
                ))
                .toList()
        );
    }
}
