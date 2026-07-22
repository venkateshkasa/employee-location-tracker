package com.employeetracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Note: accuracy is optional (may be null), but when present it must be non-negative.

public class LocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    @DecimalMin(value = "0.0", message = "Accuracy cannot be negative")
    private BigDecimal accuracy;

    // Optional: the "Nearby Colleges" radius (in meters) currently selected
    // on the employee dashboard's Radius dropdown (1/3/5/10 KM). When
    // present, the background nearby-college detection/notification job
    // uses this exact radius instead of the server's hardcoded default, so
    // the radius used for notifications always matches the radius used for
    // the map/circle/nearby search. Null (radius not selected / feature
    // off) falls back to the configured default radius.
    private Integer radiusMeters;

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
    }

    public Integer getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }
}