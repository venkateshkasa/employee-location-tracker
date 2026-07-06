package com.employeetracker.dto;

import java.math.BigDecimal;

public class LocationResponse {

    private Long locationId;
    private Long userId;
    private String employeeName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracy;
    private String locationTime;
    private String status;
    private double todayDistanceKm;

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

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
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

    public String getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(String locationTime) {
        this.locationTime = locationTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTodayDistanceKm() {
        return todayDistanceKm;
    }

    public void setTodayDistanceKm(double todayDistanceKm) {
        this.todayDistanceKm = todayDistanceKm;
    }
}
