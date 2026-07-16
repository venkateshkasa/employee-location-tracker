package com.employeetracker.controller;

import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.NearbyPlaceDto;
import com.employeetracker.entity.User;
import com.employeetracker.service.AuthService;
import com.employeetracker.service.NearbyPlaceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for nearby educational institutions API
 */
@RestController
@RequestMapping("/api/places")
public class NearbyPlaceController {

    private final NearbyPlaceService nearbyPlaceService;
    private final AuthService authService;

    public NearbyPlaceController(NearbyPlaceService nearbyPlaceService, AuthService authService) {
        this.nearbyPlaceService = nearbyPlaceService;
        this.authService = authService;
    }

    /**
     * Get nearby places for the logged-in employee.
     * <p>
     * When {@code radius}, {@code latitude} and {@code longitude} are all
     * supplied (as the employee dashboard's Radius dropdown does), this
     * performs a live search scoped to the requested radius and returns
     * only colleges/universities within it. Otherwise it falls back to the
     * original behavior of returning currently active (previously
     * persisted) nearby places, so existing callers are unaffected.
     */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyPlaceDto>>> getNearbyPlaces(
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {
        User user = authService.getCurrentUserEntity();

        if (radius != null && latitude != null && longitude != null) {
            List<NearbyPlaceDto> places =
                    nearbyPlaceService.searchNearbyPlaces(user.getUserId(), latitude, longitude, radius);
            return ResponseEntity.ok(ApiResponse.success(places));
        }

        List<NearbyPlaceDto> places = nearbyPlaceService.getActiveNearbyPlaces(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(places));
    }

    /**
     * Get nearby places history for a date range
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<NearbyPlaceDto>>> getNearbyPlacesHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        User user = authService.getCurrentUserEntity();
        List<NearbyPlaceDto> places = nearbyPlaceService.getNearbyPlacesHistory(user.getUserId(), start, end);
        return ResponseEntity.ok(ApiResponse.success(places));
    }
}