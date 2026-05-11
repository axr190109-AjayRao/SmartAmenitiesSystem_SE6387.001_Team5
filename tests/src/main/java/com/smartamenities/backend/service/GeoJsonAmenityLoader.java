package com.smartamenities.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartamenities.backend.model.Amenity;
import com.smartamenities.backend.model.AmenityType;
import com.smartamenities.backend.model.RouteGeoPoint;
import com.smartamenities.backend.model.StartLocation;
import com.smartamenities.backend.routing.RouteNetworkRouter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads amenity anchors from the canonical Level3 GeoJSON file.
 */
@Service
public class GeoJsonAmenityLoader {

    private static final String LEVEL3_FILE = "geojson/Level3.geojson";

    private final ObjectMapper objectMapper;
    private List<Amenity> cachedAmenities;
    private StartLocation cachedStartLocation;
    private List<List<RouteGeoPoint>> cachedWalkableRouteSegments;
    private RouteNetworkRouter.Graph cachedWalkableGraph;

    public GeoJsonAmenityLoader() {
        this(new ObjectMapper());
    }

    GeoJsonAmenityLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized List<Amenity> getAmenities() {
        if (cachedAmenities == null) {
            loadAll();
        }
        return cachedAmenities;
    }

    public synchronized StartLocation getStartLocation() {
        if (cachedStartLocation == null) {
            loadAll();
        }
        return cachedStartLocation;
    }

    public synchronized List<List<RouteGeoPoint>> getWalkableRouteSegments() {
        if (cachedWalkableRouteSegments == null) {
            loadAll();
        }
        return cachedWalkableRouteSegments;
    }

    public synchronized RouteNetworkRouter.Graph getWalkableGraph() {
        if (cachedWalkableGraph == null) {
            loadAll();
        }
        return cachedWalkableGraph;
    }

    private void loadAll() {
        List<Amenity> loaded = new ArrayList<>();
        List<List<RouteGeoPoint>> routeSegments = new ArrayList<>();
        Set<String> dedupeKeys = new HashSet<>();
        EnumMap<AmenityType, Integer> amenityTypeCounters = new EnumMap<>(AmenityType.class);
        List<GeoFileConfig> configs = List.of(new GeoFileConfig("Level3", LEVEL3_FILE));

        for (GeoFileConfig config : configs) {
            parseGeoJson(config, loaded, dedupeKeys, routeSegments, amenityTypeCounters);
        }

        this.cachedAmenities = Collections.unmodifiableList(loaded);
        this.cachedWalkableRouteSegments = Collections.unmodifiableList(routeSegments);
        this.cachedWalkableGraph = RouteNetworkRouter.buildGraph(this.cachedWalkableRouteSegments);
        if (cachedStartLocation == null) {
            this.cachedStartLocation = new StartLocation("Terminal D Departures", "Level3", 32.897485, -97.044391);
        }
    }

    private void parseGeoJson(
            GeoFileConfig config,
            List<Amenity> out,
            Set<String> dedupeKeys,
            List<List<RouteGeoPoint>> routeSegments,
            EnumMap<AmenityType, Integer> amenityTypeCounters
    ) {
        try (InputStream inputStream = new ClassPathResource(config.filePath()).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode features = root.path("features");
            if (!features.isArray()) {
                return;
            }

            int featureIndex = -1;
            for (JsonNode feature : features) {
                featureIndex++;
                JsonNode geometry = feature.path("geometry");
                String geometryType = geometry.path("type").asText("");

                String rawLabel = readLabel(feature.path("properties"));
                if (rawLabel == null || rawLabel.isBlank()) {
                    continue;
                }

                String cleanedLabel = cleanLabel(rawLabel);

                if ("LineString".equalsIgnoreCase(geometryType) && isRouteLabel(cleanedLabel)) {
                    List<RouteGeoPoint> polyline = parseLineString(geometry.path("coordinates"));
                    if (polyline.size() >= 2) {
                        routeSegments.add(polyline);
                    }
                    continue;
                }

                if ("MultiLineString".equalsIgnoreCase(geometryType) && isRouteLabel(cleanedLabel)) {
                    for (JsonNode lineCoordinates : geometry.path("coordinates")) {
                        List<RouteGeoPoint> polyline = parseLineString(lineCoordinates);
                        if (polyline.size() >= 2) {
                            routeSegments.add(polyline);
                        }
                    }
                    continue;
                }

                if (!"Point".equalsIgnoreCase(geometryType)) {
                    continue;
                }

                JsonNode coordinates = geometry.path("coordinates");
                if (!coordinates.isArray() || coordinates.size() < 2) {
                    continue;
                }

                double longitude = coordinates.get(0).asDouble(Double.NaN);
                double latitude = coordinates.get(1).asDouble(Double.NaN);
                if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    continue;
                }

                AmenityType amenityType = normalizeAmenityType(cleanedLabel);

                if (amenityType == null) {
                    if (isTerminalDStartAnchor(cleanedLabel)) {
                        cachedStartLocation = new StartLocation(cleanedLabel, config.level(), latitude, longitude);
                    }
                    continue;
                }

                String dedupeKey = String.format(Locale.ENGLISH, "%s|%s|%.6f|%.6f", config.level(), amenityType.name(), latitude, longitude);
                if (!dedupeKeys.add(dedupeKey)) {
                    continue;
                }

                int amenityTypeSequence = amenityTypeCounters.merge(amenityType, 1, Integer::sum);
                String amenityId = buildCanonicalAmenityId(amenityType, amenityTypeSequence);
                Amenity amenity = new Amenity(
                        amenityId,
                        cleanedLabel,
                        amenityType,
                        config.level(),
                        latitude,
                        longitude
                );
                out.add(amenity);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load GeoJSON from " + config.filePath(), ex);
        }
    }

    private static boolean isRouteLabel(String label) {
        return "route".equalsIgnoreCase(label.trim());
    }

    private static List<RouteGeoPoint> parseLineString(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray()) {
            return Collections.emptyList();
        }

        List<RouteGeoPoint> points = new ArrayList<>();
        for (JsonNode coordinate : coordinates) {
            if (!coordinate.isArray() || coordinate.size() < 2) {
                continue;
            }

            double longitude = coordinate.get(0).asDouble(Double.NaN);
            double latitude = coordinate.get(1).asDouble(Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                continue;
            }

            points.add(new RouteGeoPoint(latitude, longitude));
        }
        return points;
    }

    static AmenityType normalizeAmenityType(String label) {
        if (label == null) {
            return null;
        }

        String normalized = label.toLowerCase(Locale.ENGLISH);
        if (normalized.contains("women") || normalized.contains("womens")) {
            return AmenityType.WOMEN_RESTROOM;
        }
        if (normalized.contains("accessible") || normalized.contains("wheelchair")) {
            return AmenityType.ACCESSIBLE_RESTROOM;
        }
        if (normalized.contains("men")) {
            return AmenityType.MEN_RESTROOM;
        }
        return null;
    }

    static String cleanLabel(String value) {
        return value == null ? "" : value.replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    private static String readLabel(JsonNode properties) {
        if (properties == null || properties.isMissingNode()) {
            return null;
        }
        JsonNode lower = properties.get("label");
        if (lower != null && !lower.isNull()) {
            return lower.asText();
        }
        JsonNode upper = properties.get("Label");
        if (upper != null && !upper.isNull()) {
            return upper.asText();
        }
        return null;
    }

    private static boolean isTerminalDStartAnchor(String label) {
        String normalized = label.toLowerCase(Locale.ENGLISH);
        return normalized.contains("terminal d") && normalized.contains("departure");
    }

    private static String buildCanonicalAmenityId(AmenityType amenityType, int sequence) {
        String prefix = switch (amenityType) {
            case MEN_RESTROOM -> "MEN-RESTROOM";
            case WOMEN_RESTROOM -> "WOMEN-RESTROOM";
            case ACCESSIBLE_RESTROOM -> "ACCESSIBLE-RESTROOM";
        };
        return String.format(Locale.ENGLISH, "%s-%02d", prefix, sequence);
    }

    private record GeoFileConfig(String level, String filePath) {
    }
}
