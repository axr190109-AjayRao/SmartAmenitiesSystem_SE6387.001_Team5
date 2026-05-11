package com.smartamenities.app.model

data class InfrastructureRouteStatusRequest(
    val segmentIds: List<String>,
    val sessionSeed: String? = null
)

data class InfrastructureStatusResponse(
    val corridorBlocked: Boolean,
    val blockedSegmentId: String? = null,
    val blockedSegmentDescription: String? = null,
    val reason: String? = null,
    val alertId: String? = null,
    val estimatedClearanceMinutes: Int? = null
)
