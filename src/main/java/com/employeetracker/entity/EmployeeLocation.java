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

    @Column(name = "InsideOffice", nullable = false)
    private boolean insideOffice = false;

    @Column(name = "OfficeName", length = 150)
    private String officeName;

    @Column(name = "Address", length = 500)
    private String address;

    @Column(name = "SpeedKmph", precision = 10, scale = 2)
    private BigDecimal speedKmph;

    @Column(name = "MovementType", length = 30)
    private String movementType;

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

    public boolean isInsideOffice() {
        return insideOffice;
    }

    public void setInsideOffice(boolean insideOffice) {
        this.insideOffice = insideOffice;
    }

    public String getOfficeName() {
        return officeName;
    }

    public void setOfficeName(String officeName) {
        this.officeName = officeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getSpeedKmph() {
        return speedKmph;
    }

    public void setSpeedKmph(BigDecimal speedKmph) {
        this.speedKmph = speedKmph;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public LocalDateTime getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(LocalDateTime locationTime) {
        this.locationTime = locationTime;
    }
}
