package com.smartamenities.app.model

private const val PROGRESS_RANGE_EPSILON_METERS = 0.25

data class RouteNavigationModel(
    val destination: String,
    val accessibilityOn: Boolean,
    val estimatedTime: String,
    val startLocationName: String,
    val orderedInstructions: List<String>,
    val routeGeoPoints: List<RouteGeoPoint>,
    val startGeoPoint: RouteGeoPoint?,
    val destinationGeoPoint: RouteGeoPoint?,
    val totalDistanceMeters: Double?,
    val routeSegments: List<RouteSegmentResponse>,
    val stepProgressRanges: List<RouteStepProgressRangeResponse>,
    val terminalStepIndex: Int?,
    val maxProgressStepIndex: Int?
) {
    fun hasProgressMetadataForSimulation(): Boolean {
        val total = totalDistanceMeters ?: return false
        return total > 0.0 && routeGeoPoints.size >= 2 && stepProgressRanges.isNotEmpty()
    }

    fun resolveActiveStepIndex(progress: RouteProgressResponse, arrivalReached: Boolean): Int? {
        val terminal = terminalStepIndex ?: return null
        if (arrivalReached) return terminal

        val maxProgressIndex = maxProgressStepIndex ?: terminal
        progress.activeStepIndex?.let { backendIndex ->
            return backendIndex.coerceIn(0, maxProgressIndex)
        }

        val progressMeters = progress.clampedProgressMeters ?: return null
        val matchingIndex = stepProgressRanges.firstOrNull { range ->
            val start = range.startMeters - PROGRESS_RANGE_EPSILON_METERS
            val end = range.endMeters + PROGRESS_RANGE_EPSILON_METERS
            progressMeters in start..end
        }?.stepIndex

        return matchingIndex?.coerceIn(0, maxProgressIndex)
    }
}

fun RouteResponse.toNavigationModel(): RouteNavigationModel {
    val instructions = orderedRouteInstructions()
    val terminalStepIndex = instructions.lastIndex.takeIf { it >= 0 }
    val maxNavigableStepIndex = terminalStepIndex?.let { last -> if (last > 0) last - 1 else last }

    val normalizedSegments = routeSegments.orEmpty()
        .filter { it.lengthMeters >= 0.0 && it.cumulativeEndMeters >= it.cumulativeStartMeters }
        .map { segment ->
            val safeStepIndex = maxNavigableStepIndex?.let { maxIdx -> segment.stepIndex.coerceIn(0, maxIdx) }
                ?: segment.stepIndex.coerceAtLeast(0)
            segment.copy(stepIndex = safeStepIndex)
        }

    val normalizedProgressRanges = stepProgressRanges.orEmpty()
        .filter { it.endMeters >= it.startMeters }
        .map { range ->
            val safeStepIndex = maxNavigableStepIndex?.let { maxIdx -> range.stepIndex.coerceIn(0, maxIdx) }
                ?: range.stepIndex.coerceAtLeast(0)
            range.copy(
                stepIndex = safeStepIndex,
                instruction = range.instruction.trim()
            )
        }
        .sortedBy { it.startMeters }

    val maxProgressIndexFromMetadata = listOfNotNull(
        normalizedSegments.maxOfOrNull { it.stepIndex },
        normalizedProgressRanges.maxOfOrNull { it.stepIndex }
    ).maxOrNull()

    val maxProgressStepIndex = when {
        terminalStepIndex == null -> null
        maxNavigableStepIndex == null -> terminalStepIndex
        maxProgressIndexFromMetadata == null -> maxNavigableStepIndex
        else -> maxProgressIndexFromMetadata.coerceIn(0, maxNavigableStepIndex)
    }

    return RouteNavigationModel(
        destination = destination,
        accessibilityOn = accessibilityOn,
        estimatedTime = estimatedTime,
        startLocationName = startLocationName,
        orderedInstructions = instructions,
        routeGeoPoints = routeGeoPoints.orEmpty(),
        startGeoPoint = if (startLatitude != null && startLongitude != null) {
            RouteGeoPoint(longitude = startLongitude, latitude = startLatitude)
        } else {
            null
        },
        destinationGeoPoint = if (destinationLatitude != null && destinationLongitude != null) {
            RouteGeoPoint(longitude = destinationLongitude, latitude = destinationLatitude)
        } else {
            null
        },
        totalDistanceMeters = totalDistanceMeters,
        routeSegments = normalizedSegments,
        stepProgressRanges = normalizedProgressRanges,
        terminalStepIndex = terminalStepIndex,
        maxProgressStepIndex = maxProgressStepIndex
    )
}

