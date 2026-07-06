package com.employeetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "EmployeeLocation")
public class EmployeeLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LocationId")
    private Long locationId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "Latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "Longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "Accuracy", precision = 10, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "LocationTime", nullable = false)
    private LocalDateTime locationTime;

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public LocalDateTime getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(LocalDateTime locationTime) {
        this.locationTime = locationTime;
    }
}
