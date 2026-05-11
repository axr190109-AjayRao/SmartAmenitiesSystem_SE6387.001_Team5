package com.smartamenities.backend.routing;

import com.smartamenities.backend.model.RouteGeoPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Builds a walkable graph from GeoJSON corridor centerlines and performs shortest-path routing.
 */
public final class RouteNetworkRouter {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double NODE_MERGE_TOLERANCE_METERS = 0.75;
    private static final double EDGE_MIN_LENGTH_METERS = 0.30;
    private static final double INTERSECTION_EPSILON = 1e-9;
    private static final double ACCESSIBILITY_PENALTY_METERS = 1_000.0;
    private static final double DESTINATION_CONNECTOR_THRESHOLD_METERS = 0.50;

    private RouteNetworkRouter() {
    }

    public static Graph buildGraph(List<List<RouteGeoPoint>> routePolylines) {
        if (routePolylines == null || routePolylines.isEmpty()) {
            return new Graph();
        }

        List<RawSegment> rawSegments = dedupeSegments(extractSegments(routePolylines));
        if (rawSegments.isEmpty()) {
            return new Graph();
        }

        List<List<Double>> splitParameters = new ArrayList<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            List<Double> values = new ArrayList<>();
            values.add(0.0);
            values.add(1.0);
            splitParameters.add(values);
        }

        for (int i = 0; i < rawSegments.size(); i++) {
            RawSegment a = rawSegments.get(i);
            for (int j = i + 1; j < rawSegments.size(); j++) {
                RawSegment b = rawSegments.get(j);
                Intersection intersection = intersectSegments(a, b);
                if (intersection != null) {
                    addSplit(splitParameters.get(i), intersection.tOnA);
                    addSplit(splitParameters.get(j), intersection.tOnB);
                } else {
                    addColinearOverlapSplits(a, b, splitParameters.get(i), splitParameters.get(j));
                }
            }
        }

        Graph graph = new Graph();
        for (int i = 0; i < rawSegments.size(); i++) {
            RawSegment segment = rawSegments.get(i);
            List<Double> splits = splitParameters.get(i);
            splits.sort(Comparator.naturalOrder());

            for (int k = 1; k < splits.size(); k++) {
                double tStart = splits.get(k - 1);
                double tEnd = splits.get(k);
                if (Math.abs(tEnd - tStart) < INTERSECTION_EPSILON) {
                    continue;
                }

                PointXY p1 = segment.pointAt(tStart);
                PointXY p2 = segment.pointAt(tEnd);
                RouteGeoPoint gp1 = segment.toGeoPoint(p1);
                RouteGeoPoint gp2 = segment.toGeoPoint(p2);
                double lengthMeters = GeoMath.haversineMeters(
                        gp1.getLatitude(),
                        gp1.getLongitude(),
                        gp2.getLatitude(),
                        gp2.getLongitude()
                );
                if (lengthMeters < EDGE_MIN_LENGTH_METERS) {
                    continue;
                }

                int nodeA = graph.findOrCreateNode(gp1, NODE_MERGE_TOLERANCE_METERS);
                int nodeB = graph.findOrCreateNode(gp2, NODE_MERGE_TOLERANCE_METERS);
                if (nodeA == nodeB) {
                    continue;
                }
                graph.addUndirectedEdge(nodeA, nodeB, lengthMeters, true);
            }
        }

        return graph;
    }

    public static PathResult route(Graph graph, RouteGeoPoint startAnchor, RouteGeoPoint destinationAnchor, boolean accessibilityOn) {
        if (graph == null || graph.nodeCount() == 0 || graph.edgeCount() == 0) {
            throw new IllegalStateException("Walkable graph is empty");
        }

        SnapProjection startSnap = snapToGraph(graph, startAnchor);
        SnapProjection destinationSnap = snapToGraph(graph, destinationAnchor);

        int originalNodeCount = graph.nodeCount();
        int sourceNode = originalNodeCount;
        int targetNode = originalNodeCount + 1;

        List<List<Edge>> augmented = graph.copyAdjacencyWithExtraNodes(2);
        connectVirtualNode(augmented, sourceNode, startSnap.nodeA, startSnap.distanceToNodeA, true);
        connectVirtualNode(augmented, sourceNode, startSnap.nodeB, startSnap.distanceToNodeB, true);
        connectVirtualNode(augmented, targetNode, destinationSnap.nodeA, destinationSnap.distanceToNodeA, true);
        connectVirtualNode(augmented, targetNode, destinationSnap.nodeB, destinationSnap.distanceToNodeB, true);

        if (startSnap.sameUndirectedEdge(destinationSnap)) {
            double sameEdgeDistance = Math.abs(startSnap.distanceFromNodeA - destinationSnap.distanceFromNodeA);
            connectVirtualNode(augmented, sourceNode, targetNode, sameEdgeDistance, true);
        }

        DijkstraResult dijkstraResult = dijkstra(augmented, sourceNode, targetNode, accessibilityOn);
        if (dijkstraResult.pathNodeIds.isEmpty()) {
            throw new IllegalStateException("No walkable path found between snapped anchors");
        }

        List<RouteGeoPoint> routePolyline = new ArrayList<>();
        for (int nodeId : dijkstraResult.pathNodeIds) {
            if (nodeId == sourceNode) {
                routePolyline.add(startSnap.snappedPoint);
            } else if (nodeId == targetNode) {
                routePolyline.add(destinationSnap.snappedPoint);
            } else {
                routePolyline.add(graph.node(nodeId));
            }
        }
        routePolyline = dedupeConsecutive(routePolyline);

        double destinationConnectorMeters = GeoMath.haversineMeters(
                destinationSnap.snappedPoint.getLatitude(),
                destinationSnap.snappedPoint.getLongitude(),
                destinationAnchor.getLatitude(),
                destinationAnchor.getLongitude()
        );
        if (destinationConnectorMeters > DESTINATION_CONNECTOR_THRESHOLD_METERS) {
            routePolyline.add(destinationAnchor);
        } else if (!routePolyline.isEmpty()) {
            routePolyline.set(routePolyline.size() - 1, destinationAnchor);
            destinationConnectorMeters = 0.0;
        }

        double totalDistanceMeters = dijkstraResult.rawDistanceMeters + destinationConnectorMeters;

        return new PathResult(
                Collections.unmodifiableList(routePolyline),
                startSnap.snappedPoint,
                destinationSnap.snappedPoint,
                totalDistanceMeters,
                destinationConnectorMeters,
                dijkstraResult.pathNodeIds.size(),
                startSnap.nodeA,
                startSnap.nodeB,
                destinationSnap.nodeA,
                destinationSnap.nodeB,
                startSnap.snapDistanceMeters,
                destinationSnap.snapDistanceMeters
        );
    }

    static SnapProjection snapToGraph(Graph graph, RouteGeoPoint anchor) {
        if (graph.edgeCount() == 0) {
            throw new IllegalStateException("Cannot snap against an empty graph");
        }

        double refLatRad = Math.toRadians(anchor.getLatitude());
        double px = projectX(anchor.getLongitude(), refLatRad);
        double py = projectY(anchor.getLatitude());

        SnapProjection best = null;
        for (int nodeA = 0; nodeA < graph.nodeCount(); nodeA++) {
            for (Edge edge : graph.neighbors(nodeA)) {
                int nodeB = edge.to;
                if (nodeA >= nodeB) {
                    continue;
                }

                RouteGeoPoint a = graph.node(nodeA);
                RouteGeoPoint b = graph.node(nodeB);
                double ax = projectX(a.getLongitude(), refLatRad);
                double ay = projectY(a.getLatitude());
                double bx = projectX(b.getLongitude(), refLatRad);
                double by = projectY(b.getLatitude());

                Projection projection = projectPointToSegment(px, py, ax, ay, bx, by);
                double edgeLength = Math.max(EDGE_MIN_LENGTH_METERS, Math.hypot(bx - ax, by - ay));
                double t = projection.distanceFromA / edgeLength;
                double snappedLatitude = unprojectY(projection.y);
                double snappedLongitude = unprojectX(projection.x, refLatRad);
                RouteGeoPoint snappedPoint = new RouteGeoPoint(snappedLatitude, snappedLongitude);

                SnapProjection candidate = new SnapProjection(
                        nodeA,
                        nodeB,
                        snappedPoint,
                        Math.max(0.0, Math.min(edgeLength, projection.distanceFromA)),
                        Math.max(0.0, Math.min(edgeLength, edgeLength - projection.distanceFromA)),
                        t,
                        projection.distanceMeters,
                        edge.accessible
                );

                if (best == null || candidate.snapDistanceMeters < best.snapDistanceMeters) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("Unable to snap anchor to walkable graph");
        }
        return best;
    }

    public static double distanceToGraph(Graph graph, RouteGeoPoint anchor) {
        return snapToGraph(graph, anchor).snapDistanceMeters;
    }

    private static DijkstraResult dijkstra(List<List<Edge>> adjacency, int sourceNode, int targetNode, boolean accessibilityOn) {
        int size = adjacency.size();
        double[] distances = new double[size];
        double[] rawDistances = new double[size];
        int[] previous = new int[size];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        Arrays.fill(rawDistances, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);

        PriorityQueue<State> queue = new PriorityQueue<>(Comparator.comparingDouble(state -> state.weightedDistance));
        distances[sourceNode] = 0.0;
        rawDistances[sourceNode] = 0.0;
        queue.add(new State(sourceNode, 0.0));

        while (!queue.isEmpty()) {
            State state = queue.poll();
            if (state.weightedDistance > distances[state.nodeId] + 1e-6) {
                continue;
            }
            if (state.nodeId == targetNode) {
                break;
            }

            for (Edge edge : adjacency.get(state.nodeId)) {
                double penalty = (accessibilityOn && !edge.accessible) ? ACCESSIBILITY_PENALTY_METERS : 0.0;
                double nextWeightedDistance = state.weightedDistance + edge.meters + penalty;
                if (nextWeightedDistance + 1e-6 >= distances[edge.to]) {
                    continue;
                }

                distances[edge.to] = nextWeightedDistance;
                rawDistances[edge.to] = rawDistances[state.nodeId] + edge.meters;
                previous[edge.to] = state.nodeId;
                queue.add(new State(edge.to, nextWeightedDistance));
            }
        }

        if (Double.isInfinite(distances[targetNode])) {
            return new DijkstraResult(Collections.emptyList(), 0.0);
        }

        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int cursor = targetNode;
        while (cursor != -1) {
            stack.push(cursor);
            cursor = previous[cursor];
        }

        List<Integer> path = new ArrayList<>(stack.size());
        while (!stack.isEmpty()) {
            path.add(stack.pop());
        }
        return new DijkstraResult(path, rawDistances[targetNode]);
    }

    private static List<RawSegment> extractSegments(List<List<RouteGeoPoint>> routePolylines) {
        List<RawSegment> segments = new ArrayList<>();
        for (List<RouteGeoPoint> polyline : routePolylines) {
            if (polyline == null || polyline.size() < 2) {
                continue;
            }
            for (int i = 1; i < polyline.size(); i++) {
                RouteGeoPoint from = polyline.get(i - 1);
                RouteGeoPoint to = polyline.get(i);
                if (from == null || to == null) {
                    continue;
                }
                if (GeoMath.haversineMeters(from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude()) < EDGE_MIN_LENGTH_METERS) {
                    continue;
                }
                segments.add(new RawSegment(from, to));
            }
        }
        return segments;
    }

    private static List<RawSegment> dedupeSegments(List<RawSegment> segments) {
        Map<String, RawSegment> unique = new HashMap<>();
        for (RawSegment segment : segments) {
            String key = canonicalSegmentKey(segment.a, segment.b);
            unique.putIfAbsent(key, segment);
        }
        return new ArrayList<>(unique.values());
    }

    private static String canonicalSegmentKey(RouteGeoPoint a, RouteGeoPoint b) {
        String first = pointKey(a);
        String second = pointKey(b);
        return (first.compareTo(second) <= 0) ? first + "|" + second : second + "|" + first;
    }

    private static String pointKey(RouteGeoPoint point) {
        return String.format("%.6f,%.6f", point.getLatitude(), point.getLongitude());
    }

    private static void addSplit(List<Double> splits, double value) {
        for (double existing : splits) {
            if (Math.abs(existing - value) <= 1e-8) {
                return;
            }
        }
        splits.add(value);
    }

    private static Intersection intersectSegments(RawSegment a, RawSegment b) {
        double denominator = cross(a.dx(), a.dy(), b.dx(), b.dy());
        if (Math.abs(denominator) < INTERSECTION_EPSILON) {
            return null;
        }

        double rx = b.start.x - a.start.x;
        double ry = b.start.y - a.start.y;
        double t = cross(rx, ry, b.dx(), b.dy()) / denominator;
        double u = cross(rx, ry, a.dx(), a.dy()) / denominator;

        if (t < -INTERSECTION_EPSILON || t > 1 + INTERSECTION_EPSILON || u < -INTERSECTION_EPSILON || u > 1 + INTERSECTION_EPSILON) {
            return null;
        }
        return new Intersection(clamp01(t), clamp01(u));
    }

    private static void addColinearOverlapSplits(RawSegment a, RawSegment b, List<Double> splitsA, List<Double> splitsB) {
        if (!areColinear(a, b)) {
            return;
        }

        addProjectedEndpointIfOnSegment(b.start, a, splitsA);
        addProjectedEndpointIfOnSegment(b.end, a, splitsA);
        addProjectedEndpointIfOnSegment(a.start, b, splitsB);
        addProjectedEndpointIfOnSegment(a.end, b, splitsB);
    }

    private static void addProjectedEndpointIfOnSegment(PointXY point, RawSegment segment, List<Double> splits) {
        double t = projectionParameter(point, segment);
        if (t < -INTERSECTION_EPSILON || t > 1.0 + INTERSECTION_EPSILON) {
            return;
        }
        addSplit(splits, clamp01(t));
    }

    private static boolean areColinear(RawSegment a, RawSegment b) {
        double denominator = cross(a.dx(), a.dy(), b.dx(), b.dy());
        if (Math.abs(denominator) >= INTERSECTION_EPSILON) {
            return false;
        }
        double rx = b.start.x - a.start.x;
        double ry = b.start.y - a.start.y;
        return Math.abs(cross(rx, ry, a.dx(), a.dy())) < INTERSECTION_EPSILON;
    }

    private static double projectionParameter(PointXY point, RawSegment segment) {
        double dx = segment.dx();
        double dy = segment.dy();
        double lengthSquared = (dx * dx) + (dy * dy);
        if (lengthSquared < INTERSECTION_EPSILON) {
            return 0.0;
        }

        double px = point.x - segment.start.x;
        double py = point.y - segment.start.y;
        return ((px * dx) + (py * dy)) / lengthSquared;
    }

    private static double cross(double ax, double ay, double bx, double by) {
        return (ax * by) - (ay * bx);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static Projection projectPointToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double abx = bx - ax;
        double aby = by - ay;
        double abLengthSquared = (abx * abx) + (aby * aby);
        if (abLengthSquared < INTERSECTION_EPSILON) {
            return new Projection(ax, ay, Math.hypot(px - ax, py - ay), 0.0);
        }

        double apx = px - ax;
        double apy = py - ay;
        double t = ((apx * abx) + (apy * aby)) / abLengthSquared;
        double clampedT = clamp01(t);

        double snappedX = ax + (abx * clampedT);
        double snappedY = ay + (aby * clampedT);
        double segmentLength = Math.hypot(abx, aby);
        return new Projection(snappedX, snappedY, Math.hypot(px - snappedX, py - snappedY), clampedT * segmentLength);
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

    private static List<RouteGeoPoint> dedupeConsecutive(List<RouteGeoPoint> points) {
        if (points.isEmpty()) {
            return points;
        }

        List<RouteGeoPoint> deduped = new ArrayList<>();
        RouteGeoPoint previous = null;
        for (RouteGeoPoint point : points) {
            if (previous == null) {
                deduped.add(point);
                previous = point;
                continue;
            }

            double distance = GeoMath.haversineMeters(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    point.getLatitude(),
                    point.getLongitude()
            );
            if (distance < 0.05) {
                continue;
            }
            deduped.add(point);
            previous = point;
        }
        return deduped;
    }

    private static void connectVirtualNode(List<List<Edge>> adjacency, int from, int to, double meters, boolean accessible) {
        if (meters < 0.0) {
            return;
        }
        adjacency.get(from).add(new Edge(to, meters, accessible));
        adjacency.get(to).add(new Edge(from, meters, accessible));
    }

    public static final class Graph {
        private final List<RouteGeoPoint> nodes = new ArrayList<>();
        private final List<List<Edge>> adjacency = new ArrayList<>();
        private final Set<String> undirectedEdgeKeys = new HashSet<>();

        public int addNode(RouteGeoPoint node) {
            nodes.add(node);
            adjacency.add(new ArrayList<>());
            return nodes.size() - 1;
        }

        public int findOrCreateNode(RouteGeoPoint candidate, double toleranceMeters) {
            for (int i = 0; i < nodes.size(); i++) {
                RouteGeoPoint existing = nodes.get(i);
                double distance = GeoMath.haversineMeters(
                        existing.getLatitude(),
                        existing.getLongitude(),
                        candidate.getLatitude(),
                        candidate.getLongitude()
                );
                if (distance <= toleranceMeters) {
                    return i;
                }
            }
            return addNode(candidate);
        }

        public void addUndirectedEdge(int from, int to, double meters, boolean accessible) {
            if (from == to) {
                return;
            }
            String key = edgeKey(from, to);
            if (!undirectedEdgeKeys.add(key)) {
                return;
            }

            adjacency.get(from).add(new Edge(to, meters, accessible));
            adjacency.get(to).add(new Edge(from, meters, accessible));
        }

        public RouteGeoPoint node(int nodeId) {
            return nodes.get(nodeId);
        }

        public List<Edge> neighbors(int nodeId) {
            return adjacency.get(nodeId);
        }

        public int nodeCount() {
            return nodes.size();
        }

        public int edgeCount() {
            int directed = 0;
            for (List<Edge> edges : adjacency) {
                directed += edges.size();
            }
            return directed / 2;
        }

        public List<List<Edge>> copyAdjacencyWithExtraNodes(int extraNodes) {
            List<List<Edge>> copy = new ArrayList<>(adjacency.size() + extraNodes);
            for (List<Edge> edges : adjacency) {
                copy.add(new ArrayList<>(edges));
            }
            for (int i = 0; i < extraNodes; i++) {
                copy.add(new ArrayList<>());
            }
            return copy;
        }

        private static String edgeKey(int a, int b) {
            return (a < b) ? a + "|" + b : b + "|" + a;
        }
    }

    public record Edge(int to, double meters, boolean accessible) {
    }

    public record PathResult(
            List<RouteGeoPoint> routeGeoPoints,
            RouteGeoPoint snappedStart,
            RouteGeoPoint snappedDestination,
            double totalDistanceMeters,
            double connectorDistanceMeters,
            int pathNodeCount,
            int startNodeA,
            int startNodeB,
            int destinationNodeA,
            int destinationNodeB,
            double startSnapDistanceMeters,
            double destinationSnapDistanceMeters
    ) {
    }

    static final class SnapProjection {
        final int nodeA;
        final int nodeB;
        final RouteGeoPoint snappedPoint;
        final double distanceToNodeA;
        final double distanceToNodeB;
        final double distanceFromNodeA;
        final double snapDistanceMeters;
        final boolean accessible;

        SnapProjection(
                int nodeA,
                int nodeB,
                RouteGeoPoint snappedPoint,
                double distanceToNodeA,
                double distanceToNodeB,
                double distanceFromNodeA,
                double snapDistanceMeters,
                boolean accessible
        ) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.snappedPoint = snappedPoint;
            this.distanceToNodeA = distanceToNodeA;
            this.distanceToNodeB = distanceToNodeB;
            this.distanceFromNodeA = distanceFromNodeA;
            this.snapDistanceMeters = snapDistanceMeters;
            this.accessible = accessible;
        }

        boolean sameUndirectedEdge(SnapProjection other) {
            if (other == null) {
                return false;
            }
            return (nodeA == other.nodeA && nodeB == other.nodeB)
                    || (nodeA == other.nodeB && nodeB == other.nodeA);
        }
    }

    private record State(int nodeId, double weightedDistance) {
    }

    private record DijkstraResult(List<Integer> pathNodeIds, double rawDistanceMeters) {
    }

    private record Projection(double x, double y, double distanceMeters, double distanceFromA) {
    }

    private record Intersection(double tOnA, double tOnB) {
    }

    private static final class RawSegment {
        private final RouteGeoPoint a;
        private final RouteGeoPoint b;
        private final PointXY start;
        private final PointXY end;

        RawSegment(RouteGeoPoint a, RouteGeoPoint b) {
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
            this.start = new PointXY(a.getLongitude(), a.getLatitude());
            this.end = new PointXY(b.getLongitude(), b.getLatitude());
        }

        PointXY pointAt(double t) {
            return new PointXY(
                    start.x + ((end.x - start.x) * t),
                    start.y + ((end.y - start.y) * t)
            );
        }

        RouteGeoPoint toGeoPoint(PointXY point) {
            return new RouteGeoPoint(point.y, point.x);
        }

        double dx() {
            return end.x - start.x;
        }

        double dy() {
            return end.y - start.y;
        }
    }

    private record PointXY(double x, double y) {
        double distanceTo(PointXY other) {
            return Math.hypot(other.x - x, other.y - y);
        }
    }
}
