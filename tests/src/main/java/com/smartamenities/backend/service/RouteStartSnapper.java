package com.smartamenities.backend.service;

import com.smartamenities.backend.model.RouteGeoPoint;

import java.util.List;

/**
 * Snaps a geographic point to the nearest walkable route segment.
 */
final class RouteStartSnapper {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private RouteStartSnapper() {
    }

    static SnapResult snapToNearestSegment(double latitude, double longitude, List<List<RouteGeoPoint>> routeSegments) {
        if (routeSegments == null || routeSegments.isEmpty()) {
            return new SnapResult(latitude, longitude, 0.0);
        }

        double refLatRad = Math.toRadians(latitude);
        double startX = projectX(longitude, refLatRad);
        double startY = projectY(latitude);

        double bestDistance = Double.POSITIVE_INFINITY;
        double bestX = startX;
        double bestY = startY;

        for (List<RouteGeoPoint> polyline : routeSegments) {
            if (polyline == null || polyline.size() < 2) {
                continue;
            }

            for (int i = 1; i < polyline.size(); i++) {
                RouteGeoPoint a = polyline.get(i - 1);
                RouteGeoPoint b = polyline.get(i);

                Projection projection = projectPointToSegment(
                        startX,
                        startY,
                        projectX(a.getLongitude(), refLatRad),
                        projectY(a.getLatitude()),
                        projectX(b.getLongitude(), refLatRad),
                        projectY(b.getLatitude())
                );

                if (projection.distanceMeters < bestDistance) {
                    bestDistance = projection.distanceMeters;
                    bestX = projection.x;
                    bestY = projection.y;
                }
            }
        }

        if (Double.isInfinite(bestDistance)) {
            return new SnapResult(latitude, longitude, 0.0);
        }

        return new SnapResult(
                unprojectY(bestY),
                unprojectX(bestX, refLatRad),
                bestDistance
        );
    }

    private static Projection projectPointToSegment(
            double px,
            double py,
            double ax,
            double ay,
            double bx,
            double by
    ) {
        double abx = bx - ax;
        double aby = by - ay;
        double abLenSq = (abx * abx) + (aby * aby);
        if (abLenSq == 0.0) {
            return new Projection(ax, ay, distance(px, py, ax, ay));
        }

        double apx = px - ax;
        double apy = py - ay;
        double t = ((apx * abx) + (apy * aby)) / abLenSq;
        double clampedT = Math.max(0.0, Math.min(1.0, t));

        double snappedX = ax + (abx * clampedT);
        double snappedY = ay + (aby * clampedT);
        return new Projection(snappedX, snappedY, distance(px, py, snappedX, snappedY));
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.hypot(dx, dy);
    }

    private static double projectX(double longitude, double refLatRad) {
        return EARTH_RADIUS_METERS * Math.toRadians(longitude) * Math.cos(refLatRad);
    }

    private static double projectY(double latitude) {
        return EARTH_RADIUS_METERS * Math.toRadians(latitude);
    }

    private static double unprojectX(double x, double refLatRad) {
        return Math.toDegrees(x / (EARTH_RADIUS_METERS * Math.cos(refLatRad)));
    }

    private static double unprojectY(double y) {
        return Math.toDegrees(y / EARTH_RADIUS_METERS);
    }

    record SnapResult(double latitude, double longitude, double snapDistanceMeters) {
    }

    private record Projection(double x, double y, double distanceMeters) {
    }
}
