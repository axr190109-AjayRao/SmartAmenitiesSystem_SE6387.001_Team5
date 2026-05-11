package com.smartamenities.backend.routing;

import com.smartamenities.backend.model.RouteGeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteNetworkRouterTest {

    @Test
    void routesAlongSameCorridorSegmentWithoutOffNetworkLeap() {
        RouteNetworkRouter.Graph graph = RouteNetworkRouter.buildGraph(List.of(
                List.of(
                        new RouteGeoPoint(32.000000, -97.000000),
                        new RouteGeoPoint(32.000000, -96.998000)
                )
        ));

        RouteGeoPoint start = new RouteGeoPoint(32.000100, -96.999800);
        RouteGeoPoint destination = new RouteGeoPoint(32.000050, -96.998400);
        RouteNetworkRouter.PathResult result = RouteNetworkRouter.route(graph, start, destination, false);

        assertNotNull(result);
        assertFalse(result.routeGeoPoints().isEmpty());
        assertEquals(result.snappedStart(), result.routeGeoPoints().get(0));
        assertTrue(result.totalDistanceMeters() > 0.0);

        for (RouteGeoPoint point : result.routeGeoPoints()) {
            assertEquals(32.0, point.getLatitude(), 0.00025);
        }
    }

    @Test
    void routesAcrossIntersectionsWithTurns() {
        RouteNetworkRouter.Graph graph = RouteNetworkRouter.buildGraph(List.of(
                List.of(
                        new RouteGeoPoint(32.000000, -97.000000),
                        new RouteGeoPoint(32.000000, -96.999000),
                        new RouteGeoPoint(32.000000, -96.998000)
                ),
                List.of(
                        new RouteGeoPoint(31.999500, -96.999000),
                        new RouteGeoPoint(32.000500, -96.999000)
                )
        ));

        RouteNetworkRouter.PathResult result = RouteNetworkRouter.route(
                graph,
                new RouteGeoPoint(31.999700, -96.999000),
                new RouteGeoPoint(32.000000, -96.998100),
                false
        );

        assertTrue(result.routeGeoPoints().size() >= 3);
        assertTrue(result.pathNodeCount() >= 3);
        assertTrue(result.totalDistanceMeters() > 50.0);
    }

    @Test
    void overlappingSegmentsRemainConnectedAndRoutable() {
        RouteNetworkRouter.Graph graph = RouteNetworkRouter.buildGraph(List.of(
                List.of(
                        new RouteGeoPoint(32.001000, -97.001000),
                        new RouteGeoPoint(32.001000, -97.000000)
                ),
                List.of(
                        new RouteGeoPoint(32.001000, -97.000000),
                        new RouteGeoPoint(32.001000, -97.001000)
                ),
                List.of(
                        new RouteGeoPoint(32.001000, -97.000500),
                        new RouteGeoPoint(32.001700, -97.000500)
                )
        ));

        RouteNetworkRouter.PathResult result = RouteNetworkRouter.route(
                graph,
                new RouteGeoPoint(32.001000, -97.000900),
                new RouteGeoPoint(32.001600, -97.000500),
                false
        );

        assertTrue(result.totalDistanceMeters() > 0.0);
        assertEquals(result.snappedStart(), result.routeGeoPoints().get(0));
    }

    @Test
    void accessibilityModePrefersAccessiblePathWhenAlternativeExists() {
        RouteNetworkRouter.Graph graph = new RouteNetworkRouter.Graph();
        int node0 = graph.addNode(new RouteGeoPoint(32.0, -97.0));
        int node1 = graph.addNode(new RouteGeoPoint(32.0, -96.999));
        int node2 = graph.addNode(new RouteGeoPoint(32.0, -96.998));

        graph.addUndirectedEdge(node0, node1, 10.0, true);
        graph.addUndirectedEdge(node1, node2, 10.0, true);
        graph.addUndirectedEdge(node0, node2, 5.0, false);

        RouteGeoPoint start = graph.node(node0);
        RouteGeoPoint destination = graph.node(node2);

        RouteNetworkRouter.PathResult standard = RouteNetworkRouter.route(graph, start, destination, false);
        RouteNetworkRouter.PathResult accessible = RouteNetworkRouter.route(graph, start, destination, true);

        assertTrue(standard.totalDistanceMeters() < accessible.totalDistanceMeters());
        assertEquals(5.0, standard.totalDistanceMeters(), 0.2);
        assertEquals(20.0, accessible.totalDistanceMeters(), 0.2);
    }
}
