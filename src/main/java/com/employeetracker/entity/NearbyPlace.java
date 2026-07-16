package com.employeetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing nearby educational institutions detected near an employee's location.
 * Used for tracking universities, colleges, and other educational places within a configurable radius.
 */
@Entity
@Table(name = "NearbyPlaces")
public class NearbyPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PlaceId")
    private Long placeId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "PlaceName", nullable = false, length = 200)
    private String placeName;

    @Column(name = "PlaceType", nullable = false, length = 50)
    private String placeType;

    @Column(name = "Latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "Longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "Distance", nullable = false, precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(name = "Address", length = 500)
    private String address;

    @Column(name = "EnteredTime", nullable = false)
    private LocalDateTime enteredTime;

    @Column(name = "LeftTime")
    private LocalDateTime leftTime;

    @Column(name = "Notified", nullable = false)
    private boolean notified = false;

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
