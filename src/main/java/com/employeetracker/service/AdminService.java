package com.employeetracker.service;

import com.employeetracker.dto.AdminSummaryDto;
import com.employeetracker.dto.EmployeeDto;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.TrackingStatus;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.entity.UserStatus;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdminService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final EmployeeLocationRepository locationRepository;
    private final LocationService locationService;
    private final DistanceCalculationService distanceCalculationService;

    public AdminService(UserRepository userRepository,
                        EmployeeLocationRepository locationRepository,
                        LocationService locationService,
                        DistanceCalculationService distanceCalculationService) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.distanceCalculationService = distanceCalculationService;
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
            // A "live" marker implies the employee is actually logged in right now.
            // Without this check, an employee who logged out days ago would still
            // show a marker on the map from their last stored location.
            if (!Boolean.TRUE.equals(employee.getIsLoggedIn())) {
                continue;
            }

            locationRepository.findTopByUserIdOrderByLocationTimeDesc(employee.getUserId())
                    .ifPresent(loc -> {
                        try {
                            locations.add(locationService.getCurrentLocation(employee.getUserId()));
                        } catch (ResourceNotFoundException ignored) {
                            // Employee has no valid location record
                        }
                    });
        }

        return locations;
    }

    private EmployeeDto mapToEmployeeDto(User employee) {
        EmployeeDto dto = new EmployeeDto();
        dto.setUserId(employee.getUserId());
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setName(employee.getName());
        dto.setEmail(employee.getEmail());
        dto.setStatus(employee.getStatus().name());
        dto.setTodayDistanceKm(distanceCalculationService.calculateTodayDistanceKm(employee.getUserId()));

        Optional<EmployeeLocation> latest = locationRepository.findTopByUserIdOrderByLocationTimeDesc(employee.getUserId());
        if (latest.isPresent()) {
            EmployeeLocation loc = latest.get();
            dto.setLatitude(loc.getLatitude().doubleValue());
            dto.setLongitude(loc.getLongitude().doubleValue());
            dto.setLastUpdated(loc.getLocationTime().format(FORMATTER));
            dto.setTrackingStatus(locationService.determineTrackingStatus(employee, loc).name());
        } else {
            dto.setTrackingStatus(TrackingStatus.OFFLINE.name());
            dto.setLastUpdated("Never");
        }

        return dto;
    }
}
