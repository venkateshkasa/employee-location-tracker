package com.employeetracker.service;

import com.employeetracker.dto.AdminResetPasswordRequest;
import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.AdminNotificationDto;
import com.employeetracker.dto.CreateEmployeeRequest;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.StatusUpdateRequest;
import com.employeetracker.dto.UpdateEmployeeRequest;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.TrackingStatus;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.entity.UserStatus;
import com.employeetracker.entity.Notification;
import com.employeetracker.exception.BadRequestException;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.NotificationRepository;
import com.employeetracker.repository.UserRepository;
import com.employeetracker.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserRepository userRepository;
    private final EmployeeLocationRepository locationRepository;
    private final EmployeeActivityRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final LocationService locationService;
    private final DistanceCalculationService distanceCalculationService;
    private final NearbyPlaceService nearbyPlaceService;
    private final OfficeGeofenceService officeGeofenceService;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final String appBaseUrl;

    public AdminService(UserRepository userRepository,
                        EmployeeLocationRepository locationRepository,
                        EmployeeActivityRepository activityRepository,
                        NotificationRepository notificationRepository,
                        LocationService locationService,
                        DistanceCalculationService distanceCalculationService,
                        NearbyPlaceService nearbyPlaceService,
                        OfficeGeofenceService officeGeofenceService,
                        PasswordEncoder passwordEncoder,
                        FileStorageService fileStorageService,
                        EmailService emailService,
                        @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.activityRepository = activityRepository;
        this.notificationRepository = notificationRepository;
        this.locationService = locationService;
        this.distanceCalculationService = distanceCalculationService;
        this.nearbyPlaceService = nearbyPlaceService;
        this.officeGeofenceService = officeGeofenceService;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    public AdminSummaryDto getSummary() {
        List<User> employees = userRepository.findByRoleAndStatus(UserRole.EMPLOYEE, UserStatus.ACTIVE);

        long online = 0;
        long offline = 0;

        for (User employee : employees) {
            Optional<EmployeeLocation> latest = locationRepository.findTopByUserIdOrderByLocationTimeDesc(employee.getUserId());
            TrackingStatus status = locationService.determineTrackingStatus(
                    employee,
                    latest.orElse(null)
            );
            if (status == TrackingStatus.OFFLINE) {
                offline++;
            } else {
                online++;
            }
        }

        AdminSummaryDto summary = new AdminSummaryDto();
        summary.setTotalEmployees(employees.size());
        summary.setOnlineEmployees(online);
        summary.setOfflineEmployees(offline);
        return summary;
    }

    public List<EmployeeDto> getAllEmployees() {
        List<User> employees = userRepository.findByRoleAndStatus(UserRole.EMPLOYEE, UserStatus.ACTIVE);
        List<EmployeeDto> result = new ArrayList<>();

        for (User employee : employees) {
            result.add(mapToEmployeeDto(employee));
        }

        return result;
    }

    public EmployeeDto getEmployeeById(Long userId) {
        User employee = userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + userId));
        return mapToEmployeeDto(employee);
    }

    public List<LocationResponse> getLiveLocations() {
        List<User> employees = userRepository.findByRoleAndStatus(UserRole.EMPLOYEE, UserStatus.ACTIVE);
        List<LocationResponse> locations = new ArrayList<>();

        for (User employee : employees) {
            locationRepository.findTopByUserIdOrderByLocationTimeDesc(employee.getUserId())
                    .ifPresent(loc -> {
                        if (locationService.determineTrackingStatus(employee, loc) == TrackingStatus.OFFLINE) {
                            return;
                        }
                        try {
                            locations.add(locationService.getCurrentLocation(employee.getUserId()));
                        } catch (ResourceNotFoundException ignored) {
                            // Employee has no valid location record
                        }
                    });
        }

        return locations;
    }

    public List<AdminNotificationDto> getNotifications() {
        List<ActivityType> types = Arrays.asList(
                ActivityType.TRACKING_DISABLED,
                ActivityType.LOGOUT,
                ActivityType.ENTERED_OFFICE,
                ActivityType.EXITED_OFFICE
        );
        List<AdminNotificationDto> notifications = new ArrayList<>();
        for (EmployeeActivity activity : activityRepository.findTop20ByActivityTypeInOrderByActivityTimeDesc(types)) {
            AdminNotificationDto dto = new AdminNotificationDto();
            dto.setActivityId(activity.getActivityId());
            dto.setUserId(activity.getUserId());
            dto.setType(activity.getActivityType().name());
            dto.setMessage(activity.getDescription());
            dto.setTime(activity.getActivityTime().format(FORMATTER));
            userRepository.findById(activity.getUserId())
                    .ifPresent(user -> dto.setEmployeeName(user.getName()));
            notifications.add(dto);
        }

        // Include notifications stored in the Notifications table that were
        // raised for admin users (e.g. an employee entering a nearby college
        // area). Previously this method only looked at EmployeeActivity rows,
        // so those notifications never reached the admin dashboard even
        // though they were being created and saved correctly.
        List<Notification> adminTableNotifications = notificationRepository.findAllByOrderByCreatedAtDesc();
        java.util.Set<Long> adminUserIds = new java.util.HashSet<>();
        for (User admin : userRepository.findByRole(UserRole.ADMIN)) {
            adminUserIds.add(admin.getUserId());
        }

        // The same alert is saved once per admin user, so dedupe by
        // title+message (they're created together, at the same instant) to
        // avoid showing the same event multiple times on the dashboard.
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Notification notification : adminTableNotifications) {
            if (!adminUserIds.contains(notification.getUserId())) {
                continue;
            }
            String dedupeKey = notification.getTitle() + "|" + notification.getMessage();
            if (!seen.add(dedupeKey)) {
                continue;
            }

            AdminNotificationDto dto = new AdminNotificationDto();
            dto.setActivityId(notification.getNotificationId());
            dto.setUserId(notification.getUserId());
            dto.setType(notification.getNotificationType().name());
            dto.setMessage(notification.getTitle() + ": " + notification.getMessage());
            dto.setTime(notification.getCreatedAt().format(FORMATTER));
            notifications.add(dto);
        }

        notifications.sort(Comparator.comparing(AdminNotificationDto::getTime).reversed());

        if (notifications.size() > 20) {
            return new ArrayList<>(notifications.subList(0, 20));
        }
        return notifications;
    }

    private EmployeeDto mapToEmployeeDto(User employee) {
        EmployeeDto dto = new EmployeeDto();
        dto.setUserId(employee.getUserId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setName(employee.getName());
        dto.setEmail(employee.getEmail());
        dto.setStatus(employee.getStatus().name());
        dto.setTodayDistanceKm(distanceCalculationService.calculateTodayDistanceKm(employee.getUserId()));
        LocalDate today = LocalDate.now();
        dto.setWeeklyDistanceKm(distanceCalculationService.calculateDistanceKm(employee.getUserId(), today.with(DayOfWeek.MONDAY), today));
        dto.setMonthlyDistanceKm(distanceCalculationService.calculateDistanceKm(employee.getUserId(), today.withDayOfMonth(1), today));
        dto.setPhotoUrl(employee.getPhotoUrl());
        dto.setPhone(employee.getPhone());
        dto.setDepartment(employee.getDepartment());
        dto.setDesignation(employee.getDesignation());
        dto.setManager(employee.getManager());
        dto.setInsideOffice(employee.isInsideOffice());
        dto.setOfficeName(employee.getCurrentOfficeName());
        dto.setGender(employee.getGender());
        dto.setDateOfBirth(employee.getDateOfBirth() != null ? employee.getDateOfBirth().format(DATE_FORMATTER) : null);
        dto.setResidentialAddress(employee.getResidentialAddress());
        dto.setJoiningDate(employee.getJoiningDate() != null ? employee.getJoiningDate().format(DATE_FORMATTER) : null);
        dto.setEmployeeType(employee.getEmployeeType());
        dto.setOfficeLocation(employee.getHomeOfficeLocation());
        dto.setShift(employee.getShift());
        dto.setUsername(employee.getUsername());
        dto.setAccountStatus(employee.getStatus().name());

        Optional<EmployeeLocation> latest = locationRepository.findTopByUserIdOrderByLocationTimeDesc(employee.getUserId());
        if (latest.isPresent()) {

    EmployeeLocation loc = latest.get();

    TrackingStatus status =
            locationService.determineTrackingStatus(employee, loc);

    dto.setTrackingStatus(status.name());

    dto.setLastSeen(loc.getLocationTime().format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a")));
    dto.setInsideOffice(loc.isInsideOffice());
    dto.setOfficeName(loc.getOfficeName());
    dto.setAddress(loc.getAddress());
    dto.setMovementType(loc.getMovementType());
    if (loc.getSpeedKmph() != null) {
        dto.setSpeedKmph(loc.getSpeedKmph().doubleValue());
    }
    // Straight-line distance from the office - shown only in the employee
    // details popup/report, never in the Employee List's Distance column.
    dto.setDistanceFromOfficeKm(officeGeofenceService.distanceToNearestOfficeKm(loc.getLatitude(), loc.getLongitude()));

    if (status != TrackingStatus.OFFLINE) {
        dto.setLatitude(loc.getLatitude().doubleValue());
        dto.setLongitude(loc.getLongitude().doubleValue());
        dto.setLastUpdated(loc.getLocationTime().format(FORMATTER));
    } else {
        dto.setLastUpdated("Offline");
    }

} else {

    dto.setTrackingStatus(locationService.determineTrackingStatus(employee, null).name());
    dto.setLastUpdated("Never");

}

        return dto;
    }

    public List<com.employeetracker.dto.NearbyPlaceDto> getNearbyPlaces() {
        return nearbyPlaceService.getAllActiveNearbyPlaces();
    }

    // ==========================================================
    // Employee Management: Add / Edit / Reset Password /
    // Activate-Deactivate / Delete
    // ==========================================================

    public EmployeeDto createEmployee(CreateEmployeeRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByPhone(request.getMobile())) {
            throw new BadRequestException("Mobile Number is already in use");
        }
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        User user = new User();
        user.setEmployeeId(generateNextEmployeeId());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getMobile());
        user.setGender(request.getGender());
        user.setDateOfBirth(parseDate(request.getDateOfBirth()));
        user.setResidentialAddress(request.getAddress());
        // Decodes/validates/saves the uploaded photo to disk and stores only the
        // resulting relative URL - never the raw Base64 payload - in PhotoUrl.
        user.setPhotoUrl(fileStorageService.resolvePhotoUrl(request.getPhotoUrl()));

        user.setDepartment(request.getDepartment());
        user.setDesignation(request.getDesignation());
        user.setManager(request.getReportingManager());
        user.setJoiningDate(parseDate(request.getJoiningDate()));
        user.setEmployeeType(request.getEmployeeType());
        user.setHomeOfficeLocation(request.getOfficeLocation());
        user.setShift(request.getShift());

        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.EMPLOYEE);
        user.setStatus("INACTIVE".equalsIgnoreCase(request.getAccountStatus()) ? UserStatus.INACTIVE : UserStatus.ACTIVE);

        // Secure account-activation token: lets the employee set their own
        // password via the emailed link instead of ever being told the
        // (randomly-assigned) password created above. Valid for 24 hours.
        String setupToken = TokenUtil.generateSecureToken();
        user.setPasswordSetupToken(setupToken);
        user.setPasswordSetupTokenExpiry(LocalDateTime.now().plusHours(24));

        User saved = userRepository.save(user);
        log.info("Employee saved successfully: employeeId={}, username={}, email={}",
                saved.getEmployeeId(), saved.getUsername(), saved.getEmail());

        // The welcome email is best-effort: sendWelcomeEmail() itself already
        // catches every exception internally and returns false rather than
        // throwing, and this try/catch is a second safety net on top of that.
        // Either way, a failure here (bad SMTP config, missing/invalid Gmail
        // App Password, network issue, etc.) must never cause the
        // already-saved employee record to be rejected or rolled back.
        try {
            String passwordSetupLink = buildPasswordSetupLink(setupToken);
            boolean emailSent = emailService.sendWelcomeEmail(
                    saved.getEmail(), saved.getName(), saved.getEmployeeId(), saved.getUsername(), passwordSetupLink);
            if (!emailSent) {
                log.warn("Employee {} was created successfully but the welcome email was NOT sent. "
                        + "See the EmailService log lines above for the exact reason.", saved.getEmployeeId());
            }
        } catch (Exception ex) {
            log.error("Unexpected error while attempting to send welcome email for employee {}",
                    saved.getEmployeeId(), ex);
        }

        return mapToEmployeeDto(saved);
    }

    /**
     * Builds the clickable password-setup link using app.base-url (works
     * with ngrok / any deployed base URL, not just localhost).
     */
    private String buildPasswordSetupLink(String token) {
        String base = (appBaseUrl == null || appBaseUrl.isBlank())
                ? "http://localhost:8080"
                : appBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/setup-password?token=" + token;
    }

    public EmployeeDto updateEmployee(Long userId, UpdateEmployeeRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + userId));

        userRepository.findByEmail(request.getEmail())
                .filter(u -> !u.getUserId().equals(userId))
                .ifPresent(u -> {
                    throw new BadRequestException("Email is already in use");
                });
        userRepository.findByUsername(request.getUsername())
                .filter(u -> !u.getUserId().equals(userId))
                .ifPresent(u -> {
                    throw new BadRequestException("Username is already taken");
                });
        userRepository.findByPhone(request.getMobile())
                .filter(u -> !u.getUserId().equals(userId))
                .ifPresent(u -> {
                    throw new BadRequestException("Mobile Number is already in use");
                });

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getMobile());
        user.setGender(request.getGender());
        user.setDateOfBirth(parseDate(request.getDateOfBirth()));
        user.setResidentialAddress(request.getAddress());
        if (request.getPhotoUrl() != null) {
            user.setPhotoUrl(fileStorageService.resolvePhotoUrl(request.getPhotoUrl()));
        }

        user.setDepartment(request.getDepartment());
        user.setDesignation(request.getDesignation());
        user.setManager(request.getReportingManager());
        user.setJoiningDate(parseDate(request.getJoiningDate()));
        user.setEmployeeType(request.getEmployeeType());
        user.setHomeOfficeLocation(request.getOfficeLocation());
        user.setShift(request.getShift());

        user.setUsername(request.getUsername());
        if (request.getAccountStatus() != null) {
            user.setStatus("INACTIVE".equalsIgnoreCase(request.getAccountStatus()) ? UserStatus.INACTIVE : UserStatus.ACTIVE);
        }

        User saved = userRepository.save(user);
        return mapToEmployeeDto(saved);
    }

    public void resetPassword(Long userId, AdminResetPasswordRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + userId));

        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New Password and Confirm Password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public EmployeeDto updateStatus(Long userId, StatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + userId));

        UserStatus newStatus;
        try {
            newStatus = UserStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status must be ACTIVE or INACTIVE");
        }

        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        return mapToEmployeeDto(saved);
    }

    public void deleteEmployee(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + userId));
        userRepository.delete(user);
    }

    /**
     * Generates the next sequential Employee ID in the form EMP001, EMP002, ...
     * based on the highest existing numeric suffix, so IDs are always unique
     * regardless of deletions.
     */
    private String generateNextEmployeeId() {
        long maxNumber = 0;
        for (User user : userRepository.findAll()) {
            String id = user.getEmployeeId();
            if (id != null && id.matches("(?i)EMP\\d+")) {
                try {
                    long number = Long.parseLong(id.substring(3));
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                } catch (NumberFormatException ignored) {
                    // Non-standard existing employee id - skip it
                }
            }
        }

        String candidate;
        do {
            maxNumber++;
            candidate = String.format("EMP%03d", maxNumber);
        } while (userRepository.existsByEmployeeId(candidate));

        return candidate;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid date format, expected yyyy-MM-dd: " + value);
        }
    }
}