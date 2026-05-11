package com.smartamenities.app.ui.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object GeoJsonIndoorMapLoader {

    fun loadFromAssets(
        context: Context,
        assetPath: String = "maps/Level3.geojson"
    ): IndoorLevelMap {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val features = root.optJSONArray("features") ?: JSONArray()

        val routeSegments = mutableListOf<List<IndoorGeoPoint>>()
        val roomPolygons = mutableListOf<List<IndoorGeoPoint>>()
        val amenities = mutableListOf<IndoorAmenityAnchor>()
        val poi = mutableListOf<IndoorAmenityAnchor>()
        val allPoints = mutableListOf<IndoorGeoPoint>()

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties")
            val label = resolveLabel(properties)
            val geometry = feature.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "Point" -> {
                    val point = parsePoint(geometry.optJSONArray("coordinates")) ?: continue
                    allPoints += point
                    if (isAmenityLabel(label)) {
                        amenities += IndoorAmenityAnchor(
                            point = point,
                            label = label,
                            type = inferAmenityType(label)
                        )
                    } else {
                        poi += IndoorAmenityAnchor(
                            point = point,
                            label = label,
                            type = IndoorAmenityType.OTHER
                        )
                    }
                }

                "LineString" -> {
                    val line = parseLineString(geometry.optJSONArray("coordinates"))
                    if (line.size >= 2) {
                        allPoints += line
                        if (label.equals("Route", ignoreCase = true)) {
                            routeSegments += line
                        }
                    }
                }

                "Polygon" -> {
                    val polygon = parsePolygon(geometry.optJSONArray("coordinates"))
                    if (polygon.size >= 3) {
                        allPoints += polygon
                        roomPolygons += polygon
                    }
                }
            }
        }

        val bounds = buildBounds(allPoints)
        return IndoorLevelMap(
            bounds = bounds,
            routeSegments = routeSegments,
            roomPolygons = roomPolygons,
            amenities = amenities,
            pointsOfInterest = poi
        )
    }

    private fun parsePoint(array: JSONArray?): IndoorGeoPoint? {
        if (array == null || array.length() < 2) return null
        val longitude = array.optDouble(0, Double.NaN)
        val latitude = array.optDouble(1, Double.NaN)
        if (longitude.isNaN() || latitude.isNaN()) return null
        return IndoorGeoPoint(longitude = longitude, latitude = latitude)
    }

    private fun parseLineString(array: JSONArray?): List<IndoorGeoPoint> {
        if (array == null) return emptyList()
        val points = mutableListOf<IndoorGeoPoint>()
        for (i in 0 until array.length()) {
            parsePoint(array.optJSONArray(i))?.let { points += it }
        }
        return points
    }

    private fun parsePolygon(array: JSONArray?): List<IndoorGeoPoint> {
        if (array == null || array.length() == 0) return emptyList()
        val exteriorRing = array.optJSONArray(0)
        return parseLineString(exteriorRing)
    }

    private fun buildBounds(points: List<IndoorGeoPoint>): IndoorGeoBounds {
        if (points.isEmpty()) {
            return IndoorGeoBounds(
                minLongitude = 0.0,
                maxLongitude = 1.0,
                minLatitude = 0.0,
                maxLatitude = 1.0
            )
        }

        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY

        points.forEach { point ->
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
        }

        return IndoorGeoBounds(
            minLongitude = minLon,
            maxLongitude = maxLon,
            minLatitude = minLat,
            maxLatitude = maxLat
        )
    }

    private fun isAmenityLabel(label: String): Boolean {
        val normalized = label.lowercase()
        return normalized.contains("bathroom") ||
            normalized.contains("restroom")
    }

    private fun resolveLabel(properties: JSONObject?): String {
        if (properties == null) return ""
        return properties.optString("label")
            .ifBlank { properties.optString("Label") }
            .trim()
    }

    private fun inferAmenityType(label: String): IndoorAmenityType {
        val normalized = label.lowercase()
        return when {
            normalized.contains("accessible") || normalized.contains("wheelchair") -> IndoorAmenityType.ACCESSIBLE_RESTROOM
            normalized.contains("women") -> IndoorAmenityType.WOMEN_RESTROOM
            normalized.contains("men") -> IndoorAmenityType.MEN_RESTROOM
            else -> IndoorAmenityType.OTHER
        }
    }
}



