package com.employeetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId")
    private Long userId;

    @Column(name = "EmployeeId", nullable = false, unique = true, length = 50)
    private String employeeId;

    @Column(name = "Name", nullable = false, length = 150)
    private String name;

    @Column(name = "Email", nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "Username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "Password", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "Role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "IsLoggedIn", nullable = false)
    private boolean loggedIn = false;

    @Column(name = "TrackingEnabled", nullable = false)
    private boolean trackingEnabled = false;

    @Column(name = "PhotoUrl", length = 500)
    private String photoUrl;

    @Column(name = "Phone", length = 30)
    private String phone;

    @Column(name = "Department", length = 100)
    private String department;

    @Column(name = "Designation", length = 100)
    private String designation;

    @Column(name = "Manager", length = 150)
    private String manager;

    @Column(name = "InsideOffice", nullable = false)
    private boolean insideOffice = false;

    @Column(name = "CurrentOfficeName", length = 150)
    private String currentOfficeName;

    @Column(name = "PasswordResetToken", length = 100)
    private String passwordResetToken;

    // ---- Added for the secure "Create Password" account-activation flow ----
    // Separate from PasswordResetToken (used by the existing forgot-password
    // flow) so that flow's behavior is completely unaffected. Nullable,
    // additive columns - Hibernate's ddl-auto=update adds them automatically.

    @Column(name = "PasswordSetupToken", unique = true, length = 100)
    private String passwordSetupToken;

    @Column(name = "PasswordSetupTokenExpiry")
    private LocalDateTime passwordSetupTokenExpiry;

    @Column(name = "LastLoginTime")
    private LocalDateTime lastLoginTime;

    @Column(name = "LastLogoutTime")
    private LocalDateTime lastLogoutTime;

    // ---- Added for the heartbeat-based Online/Offline status fix ----
    // Updated every time the employee's dashboard sends a heartbeat (every
    // 30s while the dashboard is open) and once at login. Used alongside
    // IsLoggedIn to detect employees whose browser was closed/lost
    // connection without a clean logout (IsLoggedIn would otherwise stay
    // true forever). Nullable, additive column - Hibernate's ddl-auto=update
    // adds it automatically and existing rows/queries are unaffected.
    @Column(name = "LastSeenTime")
    private LocalDateTime lastSeenTime;

    // ---- Added for the Employee Management "Add Employee" / profile fields ----
    // These are purely additive (nullable) columns so existing rows/queries
    // are completely unaffected; Hibernate's ddl-auto=update adds them automatically.

    @Column(name = "Gender", length = 20)
    private String gender;

    @Column(name = "DateOfBirth")
    private java.time.LocalDate dateOfBirth;

    // The employee's personal/contact address, entered on the Add Employee form.
    // Kept separate from EmployeeLocation.address, which is the live,
    // reverse-geocoded "current location" address used by tracking.
    @Column(name = "ResidentialAddress", length = 500)
    private String residentialAddress;

    @Column(name = "JoiningDate")
    private java.time.LocalDate joiningDate;

    // FULL_TIME / CONTRACT / INTERN
    @Column(name = "EmployeeType", length = 30)
    private String employeeType;

    // The employee's assigned/home office (static, set by admin), as opposed
    // to CurrentOfficeName which is derived live from geofencing.
    @Column(name = "HomeOfficeLocation", length = 150)
    private String homeOfficeLocation;

    @Column(name = "Shift", length = 50)
    private String shift;

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public java.time.LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(java.time.LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getResidentialAddress() {
        return residentialAddress;
    }

    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }

    public java.time.LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(java.time.LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public String getEmployeeType() {
        return employeeType;
    }

    public void setEmployeeType(String employeeType) {
        this.employeeType = employeeType;
    }

    public String getHomeOfficeLocation() {
        return homeOfficeLocation;
    }

    public void setHomeOfficeLocation(String homeOfficeLocation) {
        this.homeOfficeLocation = homeOfficeLocation;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        this.trackingEnabled = trackingEnabled;
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

    public String getCurrentOfficeName() {
        return currentOfficeName;
    }

    public void setCurrentOfficeName(String currentOfficeName) {
        this.currentOfficeName = currentOfficeName;
    }

    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    public void setPasswordResetToken(String passwordResetToken) {
        this.passwordResetToken = passwordResetToken;
    }

    public String getPasswordSetupToken() {
        return passwordSetupToken;
    }

    public void setPasswordSetupToken(String passwordSetupToken) {
        this.passwordSetupToken = passwordSetupToken;
    }

    public LocalDateTime getPasswordSetupTokenExpiry() {
        return passwordSetupTokenExpiry;
    }

    public void setPasswordSetupTokenExpiry(LocalDateTime passwordSetupTokenExpiry) {
        this.passwordSetupTokenExpiry = passwordSetupTokenExpiry;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public LocalDateTime getLastLogoutTime() {
        return lastLogoutTime;
    }

    public void setLastLogoutTime(LocalDateTime lastLogoutTime) {
        this.lastLogoutTime = lastLogoutTime;
    }

    public LocalDateTime getLastSeenTime() {
        return lastSeenTime;
    }

    public void setLastSeenTime(LocalDateTime lastSeenTime) {
        this.lastSeenTime = lastSeenTime;
    }
}