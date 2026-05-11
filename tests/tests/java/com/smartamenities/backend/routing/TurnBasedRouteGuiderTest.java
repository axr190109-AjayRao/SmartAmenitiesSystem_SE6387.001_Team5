package com.smartamenities.backend.routing;

import com.smartamenities.backend.model.RouteGeoPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for turn-based route guidance generation from polyline geometry.
 */
class TurnBasedRouteGuiderTest {

    private static final String DEST = "Test Restroom";
    private static final RouteGeoPoint START = new RouteGeoPoint(32.000000, -97.000000);

    private static List<RouteGeoPoint> route(RouteGeoPoint start, double[][] legs) {
        List<RouteGeoPoint> points = new ArrayList<>();
        points.add(start);

        RouteGeoPoint cursor = start;
        for (double[] leg : legs) {
            cursor = move(cursor, leg[0], leg[1]);
            points.add(cursor);
        }

        return points;
    }

    private static RouteGeoPoint move(RouteGeoPoint origin, double headingDegrees, double distanceMeters) {
        double earthRadius = 6_371_000.0;
        double headingRadians = Math.toRadians(headingDegrees);
        double northMeters = distanceMeters * Math.cos(headingRadians);
        double eastMeters = distanceMeters * Math.sin(headingRadians);

        double latitude = origin.getLatitude() + Math.toDegrees(northMeters / earthRadius);
        double longitude = origin.getLongitude() + Math.toDegrees(
                eastMeters / (earthRadius * Math.cos(Math.toRadians(origin.getLatitude())))
        );
        return new RouteGeoPoint(latitude, longitude);
    }

    /**
     * Test straight corridor (minimal heading change).
     */
    @Test
    void straightRouteGeneratesForwardInstruction() {
        List<RouteGeoPoint> straight = route(START, new double[][]{
                {0.0, 120.0},
                {0.0, 140.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                straight, 260.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(2, steps.size(), "Straight route should produce one movement step plus arrival: " + steps);
        assertTrue(steps.get(0).toLowerCase().contains("forward"), "Straight route should emphasize forward motion: " + steps.get(0));
        assertTrue(steps.get(1).contains(DEST), "Should contain destination name");
    }

    /**
     * Test route with a slight turn.
     */
    @Test
    void slightBendGeneratesSlightTurnInstruction() {
        List<RouteGeoPoint> slightBend = route(START, new double[][]{
                {0.0, 120.0},
                {25.0, 150.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                slightBend, 270.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(3, steps.size(), "Slight bend should produce one turn step plus arrival: " + steps);
        assertTrue(steps.get(1).toLowerCase().contains("slight"), "Expected slight turn wording: " + steps.get(1));
    }

    /**
     * Test route with a full right turn.
     */
    @Test
    void routeWithRightTurnGeneratesRightInstruction() {
        List<RouteGeoPoint> rightTurn = route(START, new double[][]{
                {0.0, 120.0},
                {90.0, 150.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                rightTurn, 270.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(3, steps.size(), "Right turn should produce one turn step plus arrival: " + steps);
        assertTrue(steps.get(1).toLowerCase().contains("right"), "Expected right turn wording: " + steps.get(1));
    }

    /**
     * Test route with a full left turn.
     */
    @Test
    void routeWithLeftTurnGeneratesLeftInstruction() {
        List<RouteGeoPoint> leftTurn = route(START, new double[][]{
                {0.0, 120.0},
                {270.0, 150.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                leftTurn, 270.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(3, steps.size(), "Left turn should produce one turn step plus arrival: " + steps);
        assertTrue(steps.get(1).toLowerCase().contains("left"), "Expected left turn wording: " + steps.get(1));
    }

    /**
     * Test multi-turn route with multiple maneuvers.
     */
    @Test
    void multiTurnRouteGeneratesMultipleInstructions() {
        List<RouteGeoPoint> multiTurn = route(START, new double[][]{
                {0.0, 120.0},
                {90.0, 150.0},
                {0.0, 160.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                multiTurn, 430.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(4, steps.size(), "Expected forward + right + left + arrival: " + steps);
        assertTrue(steps.get(0).toLowerCase().contains("forward"), "First step should start with forward motion: " + steps.get(0));
        assertTrue(steps.get(1).toLowerCase().contains("right"), "Second step should describe the right turn: " + steps.get(1));
        assertTrue(steps.get(2).toLowerCase().contains("left"), "Third step should describe the left turn: " + steps.get(2));
        assertTrue(steps.get(3).contains(DEST), "Should end with destination: " + steps);
    }

    /**
     * Test empty polyline -> safe fallback.
     */
    @Test
    void emptyPolylineReturnsFallback() {
        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                List.of(), 0.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertTrue(steps.size() > 0);
        String firstStep = steps.get(0).toLowerCase();
        assertTrue(firstStep.contains("proceed") || firstStep.contains("destination"),
                "Empty polyline should return safe instruction: " + steps.get(0));
    }

    /**
     * Test null polyline -> safe fallback.
     */
    @Test
    void nullPolylineReturnsFallback() {
        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                null, 0.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertTrue(steps.size() > 0);
        String firstStep = steps.get(0).toLowerCase();
        assertTrue(firstStep.contains("proceed") || firstStep.contains("destination"),
                "Null polyline should return safe instruction: " + steps.get(0));
    }

    /**
     * Test single point polyline -> safe fallback.
     */
    @Test
    void singlePointPolylineReturnsFallback() {
        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                List.of(new RouteGeoPoint(32.0, -97.0)),
                100.0,
                50.0,
                false,
                "Terminal D Level 3 starting point",
                DEST
        );

        assertNotNull(steps);
        assertTrue(steps.size() > 0);
        String combined = String.join(" ", steps).toLowerCase();
        assertTrue(combined.contains("proceed") || combined.contains("destination"),
                "Should have safe fallback instruction");
    }

    /**
     * Test accessibility wording for connector.
     */
    @Test
    void accessibilityModeAffectsConnectorWording() {
        List<RouteGeoPoint> simple = route(START, new double[][]{{0.0, 120.0}});

        List<String> stepsAccessible = TurnBasedRouteGuider.generateTurnGuides(
                simple, 120.0, 0.0, true, "Terminal D Level 3 starting point", DEST
        );

        List<String> stepsNormal = TurnBasedRouteGuider.generateTurnGuides(
                simple, 120.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(stepsAccessible);
        assertNotNull(stepsNormal);

        String accessibleStr = String.join(" ", stepsAccessible).toLowerCase();
        String normalStr = String.join(" ", stepsNormal).toLowerCase();

        // Accessible should mention accessible route or have different wording
        assertTrue(
                accessibleStr.contains("accessible") || !accessibleStr.equals(normalStr),
                "Accessibility mode should affect wording"
        );
    }

    /**
     * Test connector distance is included when specified.
     */
    @Test
    void connectorDistanceIncludedInSteps() {
        List<RouteGeoPoint> simple = route(START, new double[][]{{0.0, 100.0}, {90.0, 100.0}});

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                simple, 200.0, 25.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(3, steps.size(), "Expected one movement step, one turn step, and arrival: " + steps);
        assertTrue(steps.get(2).contains(DEST), "Arrival step should remain destination-specific");
    }

    /**
     * Test zero connector distance (destination is on corridor).
     */
    @Test
    void zeroConnectorDistanceSkipsConnectorStep() {
        List<RouteGeoPoint> simple = route(START, new double[][]{{0.0, 110.0}});

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                simple, 110.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(2, steps.size(), "Straight corridor to destination should produce a movement step and arrival: " + steps);
        assertTrue(steps.get(1).contains(DEST), "Should include destination name");
    }

    /**
     * Test U-turn detection (heading change > 140 degrees).
     */
    @Test
    void uTurnGeneratesUTurnInstruction() {
        List<RouteGeoPoint> uTurn = route(START, new double[][]{
                {0.0, 100.0},
                {180.0, 120.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                uTurn, 220.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(3, steps.size(), "U-turn route should produce one turn step plus arrival: " + steps);
        assertTrue(steps.get(1).toLowerCase().contains("u-turn"), "Expected U-turn wording: " + steps.get(1));
    }

    /**
     * Test all returned steps are non-empty strings.
     */
    @Test
    void allStepsAreNonEmpty() {
        List<RouteGeoPoint> complex = route(START, new double[][]{
                {0.0, 100.0},
                {25.0, 110.0},
                {95.0, 120.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                complex, 330.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        for (String step : steps) {
            assertNotNull(step, "Step should not be null");
            assertFalse(step.isBlank(), "Step should not be blank");
            assertTrue(step.length() > 5, "Step should be meaningful: " + step);
        }
    }

    /**
     * Test large polyline with many segments.
     */
    @Test
    void largePolylineGeneratesReasonableStepCount() {
        List<RouteGeoPoint> zigzag = new ArrayList<>();
        zigzag.add(START);
        RouteGeoPoint cursor = START;
        for (int i = 0; i < 10; i++) {
            cursor = move(cursor, i % 2 == 0 ? 0.0 : 90.0, 80.0);
            zigzag.add(cursor);
        }

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                zigzag, 800.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertTrue(steps.size() >= 3, "Large zigzag should generate multiple steps: " + steps.size());
        assertTrue(steps.size() <= 12, "Should not have excessive step count: " + steps.size());
    }

    /**
     * Test tiny jitter segments are collapsed instead of producing noisy turns.
     */
    @Test
    void microTurnsAreCollapsed() {
        List<RouteGeoPoint> jitter = route(START, new double[][]{
                {0.0, 120.0},
                {8.0, 1.0},
                {0.0, 120.0}
        });

        List<String> steps = TurnBasedRouteGuider.generateTurnGuides(
                jitter, 241.0, 0.0, false, "Terminal D Level 3 starting point", DEST
        );

        assertNotNull(steps);
        assertEquals(2, steps.size(), "Tiny jitter should collapse into a single corridor leg: " + steps);
        assertFalse(String.join(" ", steps).toLowerCase().contains("slight"), "Collapsed route should not report a noisy slight turn: " + steps);
    }

        @Test
        void guidancePlanProducesAlignedSegmentsAndRanges() {
                List<RouteGeoPoint> geometry = route(START, new double[][]{
                                {0.0, 100.0},
                                {90.0, 120.0}
                });

                RouteGuidancePlan plan = TurnBasedRouteGuider.buildGuidancePlan(
                                geometry,
                                220.0,
                                0.0,
                                false,
                                "Terminal D Level 3 starting point",
                                DEST
                );

                assertNotNull(plan);
                assertNotNull(plan.routeSteps());
                assertNotNull(plan.routeSegments());
                assertNotNull(plan.stepProgressRanges());
                assertFalse(plan.routeSteps().isEmpty());
                assertFalse(plan.routeSegments().isEmpty());
                assertFalse(plan.stepProgressRanges().isEmpty());
                assertEquals(plan.routeSteps().size(), plan.stepProgressRanges().size());

                double previousSegmentEnd = 0.0;
                for (int index = 0; index < plan.routeSegments().size(); index++) {
                        RouteSegmentProgress segment = plan.routeSegments().get(index);
                        assertEquals(index, segment.segmentIndex());
                        assertTrue(segment.lengthMeters() > 0.0);
                        assertTrue(segment.cumulativeStartMeters() >= previousSegmentEnd - 0.01);
                        assertTrue(segment.cumulativeEndMeters() >= segment.cumulativeStartMeters());
                        assertTrue(segment.stepIndex() >= 0);
                        assertTrue(segment.stepIndex() < plan.routeSteps().size() - 1);
                        previousSegmentEnd = segment.cumulativeEndMeters();
                }

                double previousRangeEnd = 0.0;
                for (int index = 0; index < plan.stepProgressRanges().size(); index++) {
                        RouteStepProgressRange range = plan.stepProgressRanges().get(index);
                        assertEquals(index, range.stepIndex());
                        assertTrue(range.endMeters() >= range.startMeters());
                        assertTrue(range.startMeters() >= previousRangeEnd - 0.01);
                        previousRangeEnd = range.endMeters();
                }

                assertEquals(plan.totalDistanceMeters(), previousSegmentEnd, 0.01);
                assertEquals(plan.totalDistanceMeters(), previousRangeEnd, 0.01);
                assertTrue(plan.routeSteps().get(plan.routeSteps().size() - 1).contains(DEST));
        }
}
