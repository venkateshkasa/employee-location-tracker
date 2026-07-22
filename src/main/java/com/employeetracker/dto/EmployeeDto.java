package com.employeetracker.dto;

public class EmployeeDto {

    private Long userId;
    private String employeeId;
    private String name;
    private String email;
    private String status;
    private String trackingStatus;
    private double todayDistanceKm;
    private double weeklyDistanceKm;
    private double monthlyDistanceKm;
    private Double distanceFromOfficeKm;
    private String lastUpdated;
    private String lastSeen;
    private String photoUrl;
    private String phone;
    private String department;
    private String designation;
    private String manager;
    private boolean insideOffice;
    private String officeName;
    private String address;
    private Double speedKmph;
    private String movementType;
    private Double latitude;
    private Double longitude;

    // ---- Added for the Employee Management enhancements ----
    private String gender;
    private String dateOfBirth;
    private String residentialAddress;
    private String joiningDate;
    private String employeeType;
    private String officeLocation;
    private String shift;
    private String username;
    private String accountStatus;

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getResidentialAddress() {
        return residentialAddress;
    }

    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }

    public String getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(String joiningDate) {
        this.joiningDate = joiningDate;
    }

    public String getEmployeeType() {
        return employeeType;
    }

    public void setEmployeeType(String employeeType) {
        this.employeeType = employeeType;
    }

    public String getOfficeLocation() {
        return officeLocation;
    }

    public void setOfficeLocation(String officeLocation) {
        this.officeLocation = officeLocation;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrackingStatus() {
        return trackingStatus;
    }

    public void setTrackingStatus(String trackingStatus) {
        this.trackingStatus = trackingStatus;
    }

    public double getTodayDistanceKm() {
        return todayDistanceKm;
    }

    public void setTodayDistanceKm(double todayDistanceKm) {
        this.todayDistanceKm = todayDistanceKm;
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

    public Double getDistanceFromOfficeKm() {
        return distanceFromOfficeKm;
    }

    public void setDistanceFromOfficeKm(Double distanceFromOfficeKm) {
        this.distanceFromOfficeKm = distanceFromOfficeKm;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
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

    public Double getSpeedKmph() {
        return speedKmph;
    }

    public void setSpeedKmph(Double speedKmph) {
        this.speedKmph = speedKmph;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
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