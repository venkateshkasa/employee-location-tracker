package com.employeetracker.controller;

import com.employeetracker.dto.ActivityDto;
import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.LocationRequest;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.StopDto;
import com.employeetracker.entity.User;
import com.employeetracker.service.AuthService;
import com.employeetracker.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;
    private final AuthService authService;

    public LocationController(LocationService locationService, AuthService authService) {
        this.locationService = locationService;
        this.authService = authService;
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<LocationResponse>> saveLocation(@Valid @RequestBody LocationRequest request) {
        System.out.println("===== SAVE LOCATION API HIT =====");
        User user = authService.getCurrentUserEntity();
        LocationResponse response = locationService.saveLocation(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Location saved successfully", response));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<LocationResponse>> getCurrentLocation() {
        User user = authService.getCurrentUserEntity();
        LocationResponse response = locationService.getCurrentLocation(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/tracking")
    public ResponseEntity<ApiResponse<LocationResponse>> setTracking(@RequestBody Map<String, Boolean> request) {
         System.out.println("===== TRACKING API HIT =====");
        User user = authService.getCurrentUserEntity();
        boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
        LocationResponse response = locationService.setTrackingEnabled(user.getUserId(), enabled);
        return ResponseEntity.ok(ApiResponse.success(enabled ? "Tracking enabled" : "Tracking disabled", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> getLocationHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        User user = authService.getCurrentUserEntity();
        if (date == null) {
            date = LocalDate.now();
        }
        List<LocationResponse> history = locationService.getLocationHistory(user.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/distance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTodayDistance() {
        User user = authService.getCurrentUserEntity();
        double distance = locationService.getTodayDistance(user.getUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("distanceKm", distance);
        data.put("date", LocalDate.now().toString());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/stops")
    public ResponseEntity<ApiResponse<List<StopDto>>> getTodayStops() {
        User user = authService.getCurrentUserEntity();
        List<StopDto> stops = locationService.getTodayStops(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(stops));
    }

    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<List<ActivityDto>>> getTodayActivities() {
        User user = authService.getCurrentUserEntity();
        List<ActivityDto> activities = locationService.getTodayActivities(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(activities));
    }
}
