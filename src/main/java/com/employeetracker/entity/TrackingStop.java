package com.employeetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a single manual "Tracking OFF" event started by an employee via
 * the Stop Reason popup, together with the reason, optional remarks, and
 * the GPS location captured automatically at the moment tracking was
 * turned off. Distinct from {@link EmployeeStop}, which is the automatic
 * GPS-idle stop-detection table and is never touched by this feature.
 */
@Entity
@Table(name = "TrackingStops")
public class TrackingStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "StopId")
    private Long stopId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "StopReason", nullable = false, length = 30)
    private StopReason stopReason;

    @Column(name = "Remarks", length = 500)
    private String remarks;

    @Column(name = "Latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "Longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "Address", length = 500)
    private String address;

    @Column(name = "StartTime", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "EndTime")
    private LocalDateTime endTime;

    @Column(name = "DurationMinutes")
    private Integer durationMinutes;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    public Long getStopId() {
        return stopId;
    }

    public void setStopId(Long stopId) {
        this.stopId = stopId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
