package com.smartamenities.app.model

data class RouteProgressRequest(
    val routeGeoPoints: List<RouteGeoPoint>,
    val stepProgressRanges: List<RouteStepProgressRangeResponse>,
    val progressMeters: Double,
    val totalDistanceMeters: Double,
    val actualLatitude: Double? = null,
    val actualLongitude: Double? = null
)

data class RouteProgressResponse(
    val snappedLatitude: Double? = null,
    val snappedLongitude: Double? = null,
    val clampedProgressMeters: Double? = null,
    val activeStepIndex: Int? = null,
    val activeInstruction: String? = null,
    val remainingDistanceMeters: Double? = null,
    val remainingSteps: List<String>? = null,
    val arrived: Boolean? = null,
    val offRoute: Boolean? = null,
    val deviationMeters: Double? = null
)


