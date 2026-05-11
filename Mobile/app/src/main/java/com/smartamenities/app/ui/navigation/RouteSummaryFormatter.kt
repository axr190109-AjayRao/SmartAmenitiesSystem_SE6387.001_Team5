package com.smartamenities.app.ui.navigation

object RouteSummaryFormatter {
    fun formatSteps(
        orderedInstructions: List<String>,
        activeStepIndex: Int? = null
    ): String {
        if (orderedInstructions.isEmpty()) {
            return ""
        }
        return orderedInstructions.mapIndexed { index, instruction ->
            val marker = if (activeStepIndex == index) "► " else ""
            "$marker${index + 1}. $instruction"
        }.joinToString("\n")
    }
}








