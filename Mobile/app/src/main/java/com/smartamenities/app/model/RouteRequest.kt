package com.smartamenities.app.model

data class RouteRequest(
    val destination: String,
    val currentLocation: String,
    val accessibilityOn: Boolean,
    val destinationAmenityId: String? = null,
    val sessionSeed: String? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null
)

