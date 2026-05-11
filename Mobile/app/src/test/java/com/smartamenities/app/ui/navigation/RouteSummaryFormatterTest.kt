package com.smartamenities.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSummaryFormatterTest {

    @Test
    fun `preserves backend instruction order for multi-turn route`() {
        val backendInstructions = listOf(
            "Proceed forward approximately 80 meters.",
            "Slight left and continue approximately 35 meters.",
            "Right turn and continue approximately 20 meters.",
            "Arrive at Accessible Restroom."
        )

        val formatted = RouteSummaryFormatter.formatSteps(backendInstructions)

        assertEquals(
            """
                1. Proceed forward approximately 80 meters.
                2. Slight left and continue approximately 35 meters.
                3. Right turn and continue approximately 20 meters.
                4. Arrive at Accessible Restroom.
            """.trimIndent(),
            formatted
        )
    }

    @Test
    fun `keeps accessibility wording unchanged`() {
        val backendInstructions = listOf(
            "Proceed forward approximately 60 meters.",
            "Use elevator to stay on accessible path.",
            "Arrive at Women's Restroom."
        )

        val formatted = RouteSummaryFormatter.formatSteps(backendInstructions)

        assertTrue(formatted.contains("Use elevator to stay on accessible path."))
    }

    @Test
    fun `highlights the active step without rewriting text`() {
        val backendInstructions = listOf(
            "Proceed forward approximately 80 meters.",
            "Slight left and continue approximately 35 meters.",
            "Arrive at Accessible Restroom."
        )

        val formatted = RouteSummaryFormatter.formatSteps(backendInstructions, activeStepIndex = 1)

        assertTrue(formatted.contains("► 2. Slight left and continue approximately 35 meters."))
        assertTrue(formatted.contains("1. Proceed forward approximately 80 meters."))
        assertTrue(formatted.contains("3. Arrive at Accessible Restroom."))
    }

    @Test
    fun `returns empty output when backend sends no instructions`() {
        val formatted = RouteSummaryFormatter.formatSteps(emptyList())

        assertEquals("", formatted)
    }
}

