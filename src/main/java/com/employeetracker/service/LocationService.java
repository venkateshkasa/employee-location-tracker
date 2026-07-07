package com.employeetracker.service;

import com.employeetracker.config.TrackingProperties;
import com.employeetracker.dto.ActivityDto;
import com.employeetracker.dto.LocationRequest;
import com.employeetracker.dto.LocationResponse;
import com.employeetracker.dto.StopDto;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.entity.EmployeeStop;
import com.employeetracker.entity.TrackingStatus;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.EmployeeStopRepository;
import com.employeetracker.repository.UserRepository;
import com.employeetracker.util.DateTimeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmployeeLocationRepository locationRepository;
    private final EmployeeStopRepository stopRepository;
    private final EmployeeActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final StopDetectionService stopDetectionService;
    private final DistanceCalculationService distanceCalculationService;
    private final TrackingProperties trackingProperties;

    public LocationService(EmployeeLocationRepository locationRepository,
                           EmployeeStopRepository stopRepository,
                           EmployeeActivityRepository activityRepository,
                           UserRepository userRepository,
                           ActivityService activityService,
                           StopDetectionService stopDetectionService,
                           DistanceCalculationService distanceCalculationService,
                           TrackingProperties trackingProperties) {
        this.locationRepository = locationRepository;
        this.stopRepository = stopRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.stopDetectionService = stopDetectionService;
        this.distanceCalculationService = distanceCalculationService;
        this.trackingProperties = trackingProperties;
    }

    @Transactional
    public LocationResponse saveLocation(Long userId, LocationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.EMPLOYEE) {
            throw new ResourceNotFoundException("Only employees can save location updates");
        }

        // GPS tracking is considered "active" only once the employee has actually
        // sent a location update after logging in - flip the flag here rather than
        // at login time so ONLINE (logged in, no GPS yet) and MOVING/STOPPED
        // (logged in + tracking) stay distinguishable.
        if (!Boolean.TRUE.equals(user.getIsTracking())) {
            user.setIsTracking(true);
            userRepository.save(user);
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<EmployeeLocation> previousLocation = locationRepository.findTopByUserIdOrderByLocationTimeDesc(userId);

        EmployeeLocation location = new EmployeeLocation();
        location.setUserId(userId);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setAccuracy(request.getAccuracy());
        location.setLocationTime(now);

        EmployeeLocation saved = locationRepository.save(location);

        stopDetectionService.processLocationUpdate(userId, saved, previousLocation);

        activityService.logActivity(
                userId,
                ActivityType.LOCATION_UPDATE,
                "Location updated",
                saved.getLatitude(),
                saved.getLongitude(),
                saved.getLocationId()
        );

        return buildLocationResponse(user, saved);
    }

    public LocationResponse getCurrentLocation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        EmployeeLocation location = locationRepository.findTopByUserIdOrderByLocationTimeDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No location found for user"));

        return buildLocationResponse(user, location);
    }

    public List<LocationResponse> getLocationHistory(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime start = DateTimeUtil.startOfDay(date);
        LocalDateTime end = DateTimeUtil.endOfDay(date);

        return locationRepository.findTodayLocations(userId, start, end).stream()
                .map(loc -> mapToLocationResponse(user, loc))
                .collect(Collectors.toList());
    }

    public double getTodayDistance(Long userId) {
        return distanceCalculationService.calculateTodayDistanceKm(userId);
    }

    public List<StopDto> getTodayStops(Long userId) {
        return getStopsForDate(userId, LocalDate.now());
    }

    public List<StopDto> getStopsForDate(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime start = DateTimeUtil.startOfDay(date);
        LocalDateTime end = DateTimeUtil.endOfDay(date);

        return stopRepository.findUserStopsInRange(userId, start, end).stream()
                .map(stop -> mapToStopDto(user, stop))
                .collect(Collectors.toList());
    }

    public List<ActivityDto> getTodayActivities(Long userId) {
        LocalDateTime start = DateTimeUtil.startOfToday();
        LocalDateTime end = DateTimeUtil.endOfToday();

        return activityRepository.findTodayActivities(userId, start, end).stream()
                .map(this::mapToActivityDto)
                .collect(Collectors.toList());
    }

    public TrackingStatus determineTrackingStatus(User user, EmployeeLocation location) {
        // The employee's own logged-in state is authoritative. Without this check,
        // any employee with an old location row would show as ONLINE/MOVING forever,
        // even years after they last logged in.
        if (user == null || !Boolean.TRUE.equals(user.getIsLoggedIn())) {
            return TrackingStatus.OFFLINE;
        }

        if (location != null) {
            long minutesSinceUpdate = java.time.Duration.between(location.getLocationTime(), LocalDateTime.now()).toMinutes();
            if (minutesSinceUpdate > trackingProperties.getOnlineThresholdMinutes()) {
                // Safety net for a session that died without an explicit logout
                // (crashed app, closed browser) - don't keep claiming ONLINE forever.
                return TrackingStatus.OFFLINE;
            }
        }

        // Logged in, but hasn't sent a GPS update yet (or tracking hasn't started).
        if (!Boolean.TRUE.equals(user.getIsTracking()) || location == null) {
            return TrackingStatus.ONLINE;
        }

        if (stopDetectionService.isCurrentlyStopped(user.getUserId())) {
            return TrackingStatus.STOPPED;
        }

        return TrackingStatus.MOVING;
    }

    private LocationResponse buildLocationResponse(User user, EmployeeLocation location) {
        LocationResponse response = mapToLocationResponse(user, location);
        response.setTodayDistanceKm(distanceCalculationService.calculateTodayDistanceKm(user.getUserId()));
        response.setStatus(determineTrackingStatus(user, location).name());
        return response;
    }

    private LocationResponse mapToLocationResponse(User user, EmployeeLocation location) {
        LocationResponse response = new LocationResponse();
        response.setLocationId(location.getLocationId());
        response.setUserId(location.getUserId());
        response.setEmployeeName(user.getName());
        response.setLatitude(location.getLatitude());
        response.setLongitude(location.getLongitude());
        response.setAccuracy(location.getAccuracy());
        response.setLocationTime(location.getLocationTime().format(FORMATTER));
        return response;
    }

    private StopDto mapToStopDto(User user, EmployeeStop stop) {
        StopDto dto = new StopDto();
        dto.setStopId(stop.getStopId());
        dto.setUserId(stop.getUserId());
        dto.setEmployeeName(user.getName());
        dto.setLatitude(stop.getLatitude().doubleValue());
        dto.setLongitude(stop.getLongitude().doubleValue());
        dto.setStartTime(stop.getStartTime().format(FORMATTER));
        if (stop.getEndTime() != null) {
            dto.setEndTime(stop.getEndTime().format(FORMATTER));
        }
        if (stop.getDuration() != null) {
            dto.setDurationSeconds(stop.getDuration());
            dto.setDuration(DateTimeUtil.formatDuration(stop.getDuration()));
        } else if (stop.getEndTime() != null) {
            int seconds = (int) java.time.Duration.between(stop.getStartTime(), stop.getEndTime()).getSeconds();
            dto.setDurationSeconds(seconds);
            dto.setDuration(DateTimeUtil.formatDuration(seconds));
        } else {
            int seconds = (int) java.time.Duration.between(stop.getStartTime(), LocalDateTime.now()).getSeconds();
            dto.setDurationSeconds(seconds);
            dto.setDuration(DateTimeUtil.formatDuration(seconds) + " (ongoing)");
        }
        return dto;
    }

    private ActivityDto mapToActivityDto(EmployeeActivity activity) {
        ActivityDto dto = new ActivityDto();
        dto.setActivityId(activity.getActivityId());
        dto.setActivityType(activity.getActivityType().name());
        dto.setDescription(activity.getDescription());
        if (activity.getLatitude() != null) {
            dto.setLatitude(activity.getLatitude().doubleValue());
        }
        if (activity.getLongitude() != null) {
            dto.setLongitude(activity.getLongitude().doubleValue());
        }
        dto.setActivityTime(activity.getActivityTime().format(FORMATTER));
        return dto;
    }
}
