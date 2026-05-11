package com.smartamenities.backend.service;

import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.model.StartLocation;
import com.smartamenities.backend.routing.RouteNetworkRouter;
import com.smartamenities.backend.routing.GeoMath;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a stable Level 3 session start anchor from the walkable graph.
 */
@Service
public class Level3SessionStartService {

    private static final int CANDIDATE_POOL_SIZE = 8;

    private final GeoJsonAmenityLoader geoJsonAmenityLoader;
    private volatile List<StartLocation> cachedCandidates;

    public Level3SessionStartService(GeoJsonAmenityLoader geoJsonAmenityLoader) {
        this.geoJsonAmenityLoader = geoJsonAmenityLoader;
    }

    public StartLocation resolveStartLocation(String sessionSeed) {
        if (sessionSeed == null || sessionSeed.isBlank()) {
            return geoJsonAmenityLoader.getStartLocation();
        }

        List<StartLocation> candidates = getCandidates();
        if (candidates.isEmpty()) {
            return geoJsonAmenityLoader.getStartLocation();
        }

        int index = Math.floorMod(sessionSeed.hashCode(), candidates.size());
        return candidates.get(index);
    }

    private List<StartLocation> getCandidates() {
        List<StartLocation> candidates = cachedCandidates;
        if (candidates != null) {
            return candidates;
        }

        synchronized (this) {
            if (cachedCandidates != null) {
                return cachedCandidates;
            }

            StartLocation defaultStart = geoJsonAmenityLoader.getStartLocation();
            RouteNetworkRouter.Graph graph = geoJsonAmenityLoader.getWalkableGraph();
            List<StartLocationDistance> ranked = new ArrayList<>(graph.nodeCount());

            for (int nodeIndex = 0; nodeIndex < graph.nodeCount(); nodeIndex++) {
                RouteGeoPoint node = graph.node(nodeIndex);
                double distanceMeters = GeoMath.haversineMeters(
                        defaultStart.getLatitude(),
                        defaultStart.getLongitude(),
                        node.getLatitude(),
                        node.getLongitude()
                );
                ranked.add(new StartLocationDistance(node, distanceMeters));
            }

            ranked.sort(Comparator.comparingDouble(StartLocationDistance::distanceMeters));

            List<StartLocation> resolved = new ArrayList<>();
            for (int index = 0; index < ranked.size() && resolved.size() < CANDIDATE_POOL_SIZE; index++) {
                StartLocationDistance candidate = ranked.get(index);
                if (candidate.distanceMeters() <= 0.5 && resolved.isEmpty()) {
                    resolved.add(defaultStart);
                    continue;
                }

                resolved.add(new StartLocation(
                        buildCandidateName(defaultStart.getName(), index + 1),
                        defaultStart.getLevel(),
                        candidate.point().getLatitude(),
                        candidate.point().getLongitude()
                ));
            }

            if (resolved.isEmpty()) {
                resolved.add(defaultStart);
            }

            cachedCandidates = List.copyOf(resolved);
            return cachedCandidates;
        }
    }

    private static String buildCandidateName(String baseName, int candidateIndex) {
        return String.format(Locale.ENGLISH, "%s Session Start %d", baseName, candidateIndex);
    }

    private record StartLocationDistance(RouteGeoPoint point, double distanceMeters) {
    }
}