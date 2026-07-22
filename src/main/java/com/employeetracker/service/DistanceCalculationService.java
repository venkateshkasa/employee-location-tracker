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

    /**
     * Minimum movement (in meters) between two consecutive location points
     * for it to be counted as real travel. Consecutive GPS fixes that are
     * closer together than this are treated as GPS noise/jitter (the device
     * sitting still but reporting tiny fluctuations) and are not added to
     * the cumulative "today's travel distance".
     */
    private static final double MIN_MOVEMENT_METERS = 10.0;

    private final EmployeeLocationRepository locationRepository;

    public DistanceCalculationService(EmployeeLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    /**
     * Cumulative distance travelled by the employee "today" (since
     * midnight), calculated by summing the distance between consecutive GPS
     * location updates recorded today. This automatically resets every day
     * because it only ever looks at rows recorded between the start and end
     * of the current day - there is no stored counter to reset, the value
     * simply recalculates from today's rows each time it's requested.
     *
     * This is intentionally unrelated to the straight-line distance between
     * an employee and the office - see OfficeGeofenceService for that.
     */
    public double calculateTodayDistanceKm(Long userId) {
        return calculateDistanceKm(userId, LocalDate.now(), LocalDate.now());
    }

    public double calculateDistanceKm(Long userId, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime start = DateTimeUtil.startOfDay(fromDate);
        LocalDateTime end = DateTimeUtil.endOfDay(toDate);
        List<EmployeeLocation> locations = locationRepository.findTodayLocations(userId, start, end);

        if (locations.size() < 2) {
            return 0.0;
        }

        double totalMeters = 0.0;
        // Compare each new point against the last point that was actually
        // counted as movement (not just the immediately previous row), so
        // small back-and-forth GPS jitter under MIN_MOVEMENT_METERS never
        // accumulates into a fake "distance travelled" total.
        EmployeeLocation lastCounted = locations.get(0);
        for (int i = 1; i < locations.size(); i++) {
            EmployeeLocation curr = locations.get(i);
            double segmentMeters = HaversineUtil.calculateDistanceMeters(
                    lastCounted.getLatitude(), lastCounted.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );

            if (segmentMeters < MIN_MOVEMENT_METERS) {
                continue;
            }

            totalMeters += segmentMeters;
            lastCounted = curr;
        }

        return HaversineUtil.metersToKilometers(totalMeters);
    }
}