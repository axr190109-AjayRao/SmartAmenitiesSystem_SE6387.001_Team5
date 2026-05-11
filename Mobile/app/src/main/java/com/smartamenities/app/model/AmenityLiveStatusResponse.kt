package com.smartamenities.app.model

data class AmenityLiveStatusResponse(
    val amenityId: String,
    val status: String? = null,
    val statusReason: String? = null,
    val waitTimeMinutes: Int? = null,
    val stallsAvailable: Int? = null,
    val occupancyStatus: String? = null,
    val selectable: Boolean? = null
)

