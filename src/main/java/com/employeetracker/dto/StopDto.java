package com.employeetracker.dto;

public class StopDto {

    private Long stopId;
    private Long userId;
    private String employeeName;
    private double latitude;
    private double longitude;
    private String startTime;
    private String endTime;
    private String duration;
    private int durationSeconds;
    private String address;
    private String googleMapsUrl;
    private String employeeId;
    private String stopReason;
    private String stopReasonLabel;
    private String remarks;
    private boolean manual;

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

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGoogleMapsUrl() {
        return googleMapsUrl;
    }

    public void setGoogleMapsUrl(String googleMapsUrl) {
        this.googleMapsUrl = googleMapsUrl;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getStopReasonLabel() {
        return stopReasonLabel;
    }

    public void setStopReasonLabel(String stopReasonLabel) {
        this.stopReasonLabel = stopReasonLabel;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }
}