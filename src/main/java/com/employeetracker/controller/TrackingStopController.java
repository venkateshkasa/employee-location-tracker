package com.employeetracker.controller;

import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.TrackingStopDto;
import com.employeetracker.dto.TrackingStopStartRequest;
import com.employeetracker.entity.User;
import com.employeetracker.service.AuthService;
import com.employeetracker.service.TrackingStopService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee-facing endpoint for the "Add Stop" popup, opened via the
 * "+ Add Stop" button in the Stop History card. Saving records a complete
 * stop (reason, remarks, explicit Start/End time, and the employee's
 * current GPS location captured automatically) and never changes
 * Tracking ON/OFF state.
 */
@RestController
@RequestMapping("/api/tracking-stop")
public class TrackingStopController {

    private final TrackingStopService trackingStopService;
    private final AuthService authService;

    public TrackingStopController(TrackingStopService trackingStopService,
                                  AuthService authService) {
        this.trackingStopService = trackingStopService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TrackingStopDto>> addStop(
            @Valid @RequestBody TrackingStopStartRequest request) {
        User user = authService.getCurrentUserEntity();

        TrackingStopDto stop = trackingStopService.addStop(
                user.getUserId(),
                request.getStopReason(),
                request.getRemarks(),
                request.getStartTime(),
                request.getEndTime(),
                request.getLatitude(),
                request.getLongitude()
        );

        return ResponseEntity.ok(ApiResponse.success("Stop added", stop));
    }
}
