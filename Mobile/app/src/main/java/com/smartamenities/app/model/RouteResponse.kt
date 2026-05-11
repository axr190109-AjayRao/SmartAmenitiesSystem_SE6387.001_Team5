package com.smartamenities.app.model

import com.google.gson.annotations.SerializedName

data class RouteResponse(
    val destination: String,
    val accessibilityOn: Boolean,
    val estimatedTime: String,
    val routeSteps: List<RouteStepResponse>? = null,
    val startLocationName: String,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val startPixelX: Float? = null,
    val startPixelY: Float? = null,
    val destinationPixelX: Float? = null,
    val destinationPixelY: Float? = null,
    val routePixelPoints: List<RoutePixelPoint>? = null,
    val totalDistanceMeters: Double? = null,
    val routeSegments: List<RouteSegmentResponse>? = null,
    val stepProgressRanges: List<RouteStepProgressRangeResponse>? = null,
    @SerializedName(value = "routeGeoPoints", alternate = ["routeCoordinates", "routePointsGeo"])
    val routeGeoPoints: List<RouteGeoPoint>? = null
) {
    fun orderedRouteInstructions(): List<String> {
        return routeSteps.orEmpty()
            .map { it.instruction.trim() }
            .filter { it.isNotBlank() }
    }

    fun hasProgressMetadata(): Boolean {
        return routeGeoPoints.orEmpty().size >= 2 &&
            !stepProgressRanges.isNullOrEmpty() &&
            (totalDistanceMeters ?: 0.0) > 0.0
    }
}

data class RoutePixelPoint(
    val x: Float,
    val y: Float
)

data class RouteGeoPoint(
    val longitude: Double,
    val latitude: Double
)

data class RouteSegmentResponse(
    val segmentIndex: Int,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val lengthMeters: Double,
    val cumulativeStartMeters: Double,
    val cumulativeEndMeters: Double,
    val stepIndex: Int
)

data class RouteStepProgressRangeResponse(
    val stepIndex: Int,
    val startMeters: Double,
    val endMeters: Double,
    val instruction: String = ""
)

data class RouteStepResponse(
    @SerializedName(value = "instruction", alternate = ["text", "step"])
    val instruction: String = ""
)

