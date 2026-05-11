package com.smartamenities.app.ui.navigation

data class IndoorGeoPoint(
    val longitude: Double,
    val latitude: Double
)

data class IndoorGeoBounds(
    val minLongitude: Double,
    val maxLongitude: Double,
    val minLatitude: Double,
    val maxLatitude: Double
) {
    val longitudeSpan: Double get() = (maxLongitude - minLongitude).coerceAtLeast(1e-9)
    val latitudeSpan: Double get() = (maxLatitude - minLatitude).coerceAtLeast(1e-9)
}

enum class IndoorAmenityType {
    MEN_RESTROOM,
    WOMEN_RESTROOM,
    ACCESSIBLE_RESTROOM,
    OTHER
}

data class IndoorAmenityAnchor(
    val point: IndoorGeoPoint,
    val label: String,
    val type: IndoorAmenityType
)

data class IndoorLevelMap(
    val bounds: IndoorGeoBounds,
    val routeSegments: List<List<IndoorGeoPoint>>,
    val roomPolygons: List<List<IndoorGeoPoint>>,
    val amenities: List<IndoorAmenityAnchor>,
    val pointsOfInterest: List<IndoorAmenityAnchor>
)

