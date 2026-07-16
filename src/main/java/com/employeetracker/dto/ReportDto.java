package com.employeetracker.dto;

import java.util.List;

public class ReportDto {

    private String fromDate;
    private String toDate;
    private Long userId;
    private String employeeName;
    private String employeeId;
    private double totalDistanceKm;
    private int totalStops;
    private int totalLocationUpdates;
    private String checkInTime;
    private String checkOutTime;
    private String totalWorkingHours;
    private String totalIdleTime;
    private double dailyDistanceKm;
    private double weeklyDistanceKm;
    private double monthlyDistanceKm;
    private List<LocationResponse> locations;
    private List<StopDto> stops;
    private List<ActivityDto> activities;

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
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

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public int getTotalStops() {
        return totalStops;
    }

    public void setTotalStops(int totalStops) {
        this.totalStops = totalStops;
    }

    public int getTotalLocationUpdates() {
        return totalLocationUpdates;
    }

    public void setTotalLocationUpdates(int totalLocationUpdates) {
        this.totalLocationUpdates = totalLocationUpdates;
    }

    public String getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(String checkInTime) {
        this.checkInTime = checkInTime;
    }

    public String getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(String checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getTotalWorkingHours() {
        return totalWorkingHours;
    }

    public void setTotalWorkingHours(String totalWorkingHours) {
        this.totalWorkingHours = totalWorkingHours;
    }

    public String getTotalIdleTime() {
        return totalIdleTime;
    }

    public void setTotalIdleTime(String totalIdleTime) {
        this.totalIdleTime = totalIdleTime;
    }

    public double getDailyDistanceKm() {
        return dailyDistanceKm;
    }

    public void setDailyDistanceKm(double dailyDistanceKm) {
        this.dailyDistanceKm = dailyDistanceKm;
    }

    public double getWeeklyDistanceKm() {
        return weeklyDistanceKm;
    }

    public void setWeeklyDistanceKm(double weeklyDistanceKm) {
        this.weeklyDistanceKm = weeklyDistanceKm;
    }

    public double getMonthlyDistanceKm() {
        return monthlyDistanceKm;
    }

    public void setMonthlyDistanceKm(double monthlyDistanceKm) {
        this.monthlyDistanceKm = monthlyDistanceKm;
    }

    public List<LocationResponse> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationResponse> locations) {
        this.locations = locations;
    }

    public List<StopDto> getStops() {
        return stops;
    }

    public void setStops(List<StopDto> stops) {
        this.stops = stops;
    }

    public List<ActivityDto> getActivities() {
        return activities;
    }

    public void setActivities(List<ActivityDto> activities) {
        this.activities = activities;
    }
}
