package com.employeetracker.service;

import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.AdminNotificationDto;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.TrackingStatus;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.entity.UserStatus;
import com.employeetracker.entity.Notification;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.NotificationRepository;
import com.employeetracker.repository.UserRepository;
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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final EmployeeLocationRepository locationRepository;
    private final EmployeeActivityRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final LocationService locationService;
    private final DistanceCalculationService distanceCalculationService;
    private final NearbyPlaceService nearbyPlaceService;

    public AdminService(UserRepository userRepository,
                        EmployeeLocationRepository locationRepository,
                        EmployeeActivityRepository activityRepository,
                        NotificationRepository notificationRepository,
                        LocationService locationService,
                        DistanceCalculationService distanceCalculationService,
                        NearbyPlaceService nearbyPlaceService) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.activityRepository = activityRepository;
        this.notificationRepository = notificationRepository;
        this.locationService = locationService;
        this.distanceCalculationService = distanceCalculationService;
        this.nearbyPlaceService = nearbyPlaceService;
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
}