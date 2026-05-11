package com.smartamenities.app.model

data class SessionResetResponse(
    val sessionSeed: String,
    val clearedClosureTriggerCount: Int
)

