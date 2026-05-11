package com.smartamenities.backend.service;

import com.smartamenities.backend.integration.location.LocationProvider;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.model.StartLocation;
import com.smartamenities.backend.routing.GeoMath;
import com.smartamenities.backend.routing.RouteNetworkRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Shared route metrics service used by both /route and /amenities.
 */
@Service
public class RouteMetricsService {

    private final GeoJsonAmenityLoader geoJsonAmenityLoader;
    private final LocationProvider locationProvider;

    @Autowired
    public RouteMetricsService(GeoJsonAmenityLoader geoJsonAmenityLoader, LocationProvider locationProvider) {
        this.geoJsonAmenityLoader = geoJsonAmenityLoader;
        this.locationProvider = locationProvider;
    }

    public RouteComputation computeRouteMetrics(
            Amenity destinationAmenity,
            boolean accessibilityOn,
            Double currentLat,
            Double currentLon
    ) {
        return computeRouteMetrics(destinationAmenity, accessibilityOn, currentLat, currentLon, null);
    }

    public RouteComputation computeRouteMetrics(
            Amenity destinationAmenity,
            boolean accessibilityOn,
            Double currentLat,
            Double currentLon,
            String sessionSeed
    ) {
        StartLocation startLocation = resolveStartLocation(currentLat, currentLon, sessionSeed);
        RouteGeoPoint startAnchor = new RouteGeoPoint(startLocation.getLatitude(), startLocation.getLongitude());
        RouteGeoPoint destinationAnchor = new RouteGeoPoint(destinationAmenity.getLatitude(), destinationAmenity.getLongitude());

        RouteNetworkRouter.PathResult pathResult = RouteNetworkRouter.route(
                geoJsonAmenityLoader.getWalkableGraph(),
                startAnchor,
                destinationAnchor,
                accessibilityOn
        );

        return new RouteComputation(startLocation, startAnchor, destinationAnchor, pathResult);
    }

    StartLocation resolveStartLocation(Double currentLat, Double currentLon, String sessionSeed) {
        return locationProvider.resolveStartLocation(
                currentLat,
                currentLon,
                sessionSeed,
                geoJsonAmenityLoader.getStartLocation()
        );
    }

    public record RouteComputation(
            StartLocation startLocation,
            RouteGeoPoint requestedStartAnchor,
            RouteGeoPoint destinationAnchor,
            RouteNetworkRouter.PathResult pathResult
    ) {
        public double routeDistanceMeters() {
            return polylineDistanceMeters(pathResult.routeGeoPoints());
        }

        private static double polylineDistanceMeters(List<RouteGeoPoint> points) {
            if (points == null || points.size() < 2) {
                return 0.0;
            }

            double total = 0.0;
            for (int i = 1; i < points.size(); i++) {
                RouteGeoPoint previous = points.get(i - 1);
                RouteGeoPoint current = points.get(i);
                total += GeoMath.haversineMeters(
                        previous.getLatitude(),
                        previous.getLongitude(),
                        current.getLatitude(),
                        current.getLongitude()
                );
            }
            return total;
        }
    }
}
