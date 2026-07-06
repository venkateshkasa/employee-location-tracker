package com.employeetracker.service;

import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.util.DateTimeUtil;
import com.employeetracker.util.HaversineUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DistanceCalculationService {

    private final EmployeeLocationRepository locationRepository;

    public DistanceCalculationService(EmployeeLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public double calculateTodayDistanceKm(Long userId) {
        return calculateDistanceKm(userId, LocalDate.now());
    }

    public double calculateDistanceKm(Long userId, LocalDate date) {
        LocalDateTime start = DateTimeUtil.startOfDay(date);
        LocalDateTime end = DateTimeUtil.endOfDay(date);
        List<EmployeeLocation> locations = locationRepository.findTodayLocations(userId, start, end);

        if (locations.size() < 2) {
            return 0.0;
        }

        double totalMeters = 0.0;
        for (int i = 1; i < locations.size(); i++) {
            EmployeeLocation prev = locations.get(i - 1);
            EmployeeLocation curr = locations.get(i);
            totalMeters += HaversineUtil.calculateDistanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
        }

        return HaversineUtil.metersToKilometers(totalMeters);
    }
}
