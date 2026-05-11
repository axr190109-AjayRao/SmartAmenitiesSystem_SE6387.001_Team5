package com.smartamenities.app.model

/**
 * Amenity data returned by GET /amenities endpoint.
 * 
 * distanceMeters is route-network distance (not haversine) computed by backend
 * using the same graph routing engine as POST /route.
 * Android treats this as authoritative and does NOT recompute distance.
 */
data class AmenityResponse(
    val id: String,
    val name: String,
    val amenityType: String,
    val level: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double? = null,  // Backend-computed route distance; do not override in Android
    val status: String? = null,
    val statusReason: String? = null,
    val waitTimeMinutes: Int? = null,
    val stallsAvailable: Int? = null,
    val occupancyStatus: String? = null,
    val selectable: Boolean? = null
)

