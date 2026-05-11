package com.smartamenities.backend.service;

import com.smartamenities.backend.dto.RouteGeoPoint;
import com.smartamenities.backend.dto.RouteProgressRequest;
import com.smartamenities.backend.dto.RouteProgressResponse;
import com.smartamenities.backend.dto.RouteStepProgressRangeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteProgressServiceTest {

    @Test
    void goingStraightInsteadOfTurningTriggersOffRoute() {
        RouteProgressService service = new RouteProgressService();

        List<RouteGeoPoint> points = List.of(
                new RouteGeoPoint(0.0, 0.0),
                new RouteGeoPoint(0.0, 0.0003),
                new RouteGeoPoint(0.0003, 0.0003)
        );
        List<RouteStepProgressRangeResponse> ranges = List.of(
                new RouteStepProgressRangeResponse(0, 0.0, 33.0, "Proceed forward for 33 meters."),
                new RouteStepProgressRangeResponse(1, 33.0, 66.0, "Turn right and continue for 33 meters."),
                new RouteStepProgressRangeResponse(2, 66.0, 66.0, "Arrive at destination.")
        );

        RouteProgressResponse response = service.simulateProgress(new RouteProgressRequest(
                points,
                ranges,
                45.0,
                66.0,
                0.0,
                0.0006
        ));

        assertTrue(response.isOffRoute());
    }

    @Test
    void turningInsteadOfGoingStraightTriggersOffRoute() {
        RouteProgressService service = new RouteProgressService();

        List<RouteGeoPoint> points = List.of(
                new RouteGeoPoint(0.0, 0.0),
                new RouteGeoPoint(0.0, 0.0008)
        );
        List<RouteStepProgressRangeResponse> ranges = List.of(
                new RouteStepProgressRangeResponse(0, 0.0, 89.0, "Proceed forward for 89 meters."),
                new RouteStepProgressRangeResponse(1, 89.0, 89.0, "Arrive at destination.")
        );

        RouteProgressResponse response = service.simulateProgress(new RouteProgressRequest(
                points,
                ranges,
                20.0,
                89.0,
                0.0004,
                0.00018
        ));

        assertTrue(response.isOffRoute());
    }

    @Test
    void backtrackingAlongRouteTriggersOffRoute() {
        RouteProgressService service = new RouteProgressService();

        List<RouteGeoPoint> points = List.of(
                new RouteGeoPoint(0.0, 0.0),
                new RouteGeoPoint(0.0, 0.001)
        );
        List<RouteStepProgressRangeResponse> ranges = List.of(
                new RouteStepProgressRangeResponse(0, 0.0, 111.0, "Proceed forward for 111 meters."),
                new RouteStepProgressRangeResponse(1, 111.0, 111.0, "Arrive at destination.")
        );

        RouteProgressResponse response = service.simulateProgress(new RouteProgressRequest(
                points,
                ranges,
                85.0,
                111.0,
                0.0,
                0.0002
        ));

        assertTrue(response.isOffRoute());
    }
}
