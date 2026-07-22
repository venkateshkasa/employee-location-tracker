package com.employeetracker.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Payload sent when the employee saves the "Add Stop" popup (opened via the
 * "+ Add Stop" button in the Stop History card). This is a standalone,
 * complete stop record - Start Time and End Time are both entered by the
 * employee (validated server-side so End Time is strictly after Start
 * Time), and saving it never changes Tracking ON/OFF state. Latitude/
 * Longitude come from the employee's current GPS reading, already
 * available on the dashboard (never entered manually).
 */
public class TrackingStopStartRequest {

    @NotNull(message = "Stop reason is required")
    private String stopReason;

    private String remarks;

    /** Time of day, "HH:mm" (24-hour), combined with today's date. */
    @NotNull(message = "Start time is required")
    private String startTime;

    /** Time of day, "HH:mm" (24-hour), combined with today's date. */
    @NotNull(message = "End time is required")
    private String endTime;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
