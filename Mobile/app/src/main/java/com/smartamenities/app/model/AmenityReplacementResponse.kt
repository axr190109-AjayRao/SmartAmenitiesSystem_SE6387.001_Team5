package com.smartamenities.app.model

data class AmenityReplacementResponse(
    val amenityId: String,
    val amenityName: String,
    val amenityType: String,
    val distanceMeters: Double? = null,
    val waitTimeMinutes: Int? = null,
    val stallsAvailable: Int? = null,
    val occupancyStatus: String? = null
)

