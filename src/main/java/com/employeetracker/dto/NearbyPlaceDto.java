package com.employeetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for nearby place information
 */
public class NearbyPlaceDto {

    private Long placeId;
    private Long userId;
    private String placeName;
    private String placeType;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal distance;
    private String address;
    private LocalDateTime enteredTime;
    private LocalDateTime leftTime;
    private boolean notified;

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getPlaceType() {
        return placeType;
    }

    public void setPlaceType(String placeType) {
        this.placeType = placeType;
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

    public BigDecimal getDistance() {
        return distance;
    }

    public void setDistance(BigDecimal distance) {
        this.distance = distance;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getEnteredTime() {
        return enteredTime;
    }

    public void setEnteredTime(LocalDateTime enteredTime) {
        this.enteredTime = enteredTime;
    }

    public LocalDateTime getLeftTime() {
        return leftTime;
    }

    public void setLeftTime(LocalDateTime leftTime) {
        this.leftTime = leftTime;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }
}
