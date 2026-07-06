package com.employeetracker.service;

import com.employeetracker.config.TrackingProperties;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.EmployeeStop;
import com.employeetracker.repository.EmployeeStopRepository;
import com.employeetracker.util.GeoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class StopDetectionService {

    private final EmployeeStopRepository stopRepository;
    private final ActivityService activityService;
    private final TrackingProperties trackingProperties;

    public StopDetectionService(EmployeeStopRepository stopRepository,
                                ActivityService activityService,
                                TrackingProperties trackingProperties) {
        this.stopRepository = stopRepository;
        this.activityService = activityService;
        this.trackingProperties = trackingProperties;
    }

    @Transactional
    public void processLocationUpdate(Long userId, EmployeeLocation currentLocation,
                                      Optional<EmployeeLocation> previousLocation) {
        double radiusMeters = trackingProperties.getStopRadiusMeters();
        int durationMinutes = trackingProperties.getStopDurationMinutes();

        Optional<EmployeeStop> openStop = stopRepository.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId);

        if (previousLocation.isEmpty()) {
            return;
        }

        EmployeeLocation previous = previousLocation.get();
        boolean withinRadius = GeoUtil.isWithinRadiusMeters(
                previous.getLatitude(), previous.getLongitude(),
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                radiusMeters
        );

        if (withinRadius) {
            handlePotentialStop(userId, currentLocation, openStop, durationMinutes);
        } else {
            closeOpenStopIfExists(openStop, currentLocation.getLocationTime());
        }
    }

    private void handlePotentialStop(Long userId, EmployeeLocation currentLocation,
                                     Optional<EmployeeStop> openStop, int durationMinutes) {
        if (openStop.isPresent()) {
            EmployeeStop stop = openStop.get();
            long minutesElapsed = Duration.between(stop.getStartTime(), currentLocation.getLocationTime()).toMinutes();
            if (minutesElapsed >= durationMinutes && stop.getEndTime() == null) {
                // Stop is confirmed - already open, update duration if employee still stopped
                int durationSeconds = (int) Duration.between(stop.getStartTime(), currentLocation.getLocationTime()).getSeconds();
                stop.setDuration(durationSeconds);
                stopRepository.save(stop);
            }
            return;
        }

        EmployeeStop newStop = new EmployeeStop();
        newStop.setUserId(userId);
        newStop.setLatitude(currentLocation.getLatitude());
        newStop.setLongitude(currentLocation.getLongitude());
        newStop.setStartTime(currentLocation.getLocationTime());
        EmployeeStop saved = stopRepository.save(newStop);

        activityService.logActivity(
                userId,
                ActivityType.STOP,
                "Employee stopped",
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                saved.getStopId()
        );
    }

    @Transactional
    public void closeOpenStopIfExists(Optional<EmployeeStop> openStop, LocalDateTime endTime) {
        if (openStop.isEmpty()) {
            return;
        }

        EmployeeStop stop = openStop.get();
        if (stop.getEndTime() != null) {
            return;
        }

        stop.setEndTime(endTime);
        int durationSeconds = (int) Duration.between(stop.getStartTime(), endTime).getSeconds();
        stop.setDuration(durationSeconds);
        stopRepository.save(stop);
    }

    public boolean isCurrentlyStopped(Long userId) {
        Optional<EmployeeStop> openStop = stopRepository.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId);
        if (openStop.isEmpty()) {
            return false;
        }

        EmployeeStop stop = openStop.get();
        long minutesElapsed = Duration.between(stop.getStartTime(), LocalDateTime.now()).toMinutes();
        return minutesElapsed >= trackingProperties.getStopDurationMinutes();
    }
}
