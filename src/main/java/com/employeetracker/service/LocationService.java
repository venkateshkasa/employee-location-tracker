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
import com.employeetracker.entity.TrackingStop;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.exception.BadRequestException;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.repository.EmployeeStopRepository;
import com.employeetracker.repository.TrackingStopRepository;
import com.employeetracker.repository.UserRepository;
import com.employeetracker.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmployeeLocationRepository locationRepository;
    private final EmployeeStopRepository stopRepository;
    private final EmployeeActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final StopDetectionService stopDetectionService;
    private final DistanceCalculationService distanceCalculationService;
    private final TrackingProperties trackingProperties;
    private final ReverseGeocodingService reverseGeocodingService;
    private final OfficeGeofenceService officeGeofenceService;
    private final SpeedService speedService;
    private final NearbyPlaceService nearbyPlaceService;
    private final NotificationService notificationService;
    private final TrackingStopService trackingStopService;
    private final TrackingStopRepository trackingStopRepository;

    public LocationService(EmployeeLocationRepository locationRepository,
                           EmployeeStopRepository stopRepository,
                           EmployeeActivityRepository activityRepository,
                           UserRepository userRepository,
                           ActivityService activityService,
                           StopDetectionService stopDetectionService,
                           DistanceCalculationService distanceCalculationService,
                           TrackingProperties trackingProperties,
                           ReverseGeocodingService reverseGeocodingService,
                           OfficeGeofenceService officeGeofenceService,
                           SpeedService speedService,
                           NearbyPlaceService nearbyPlaceService,
                           NotificationService notificationService,
                           TrackingStopService trackingStopService,
                           TrackingStopRepository trackingStopRepository) {
        this.locationRepository = locationRepository;
        this.stopRepository = stopRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.stopDetectionService = stopDetectionService;
        this.distanceCalculationService = distanceCalculationService;
        this.trackingProperties = trackingProperties;
        this.reverseGeocodingService = reverseGeocodingService;
        this.officeGeofenceService = officeGeofenceService;
        this.speedService = speedService;
        this.nearbyPlaceService = nearbyPlaceService;
        this.notificationService = notificationService;
        this.trackingStopService = trackingStopService;
        this.trackingStopRepository = trackingStopRepository;
    }

    @Transactional
    public LocationResponse saveLocation(Long userId, LocationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.EMPLOYEE) {
            throw new ResourceNotFoundException("Only employees can save location updates");
        }

        if (!user.isLoggedIn() || !user.isTrackingEnabled()) {
            throw new BadRequestException("Tracking is disabled for this employee");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<EmployeeLocation> previousLocation = locationRepository.findTopByUserIdOrderByLocationTimeDesc(userId);

        EmployeeLocation location = new EmployeeLocation();
        location.setUserId(userId);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setAccuracy(request.getAccuracy());
        location.setLocationTime(now);

        SpeedService.SpeedResult speedResult = speedService.calculate(location, previousLocation);
        location.setSpeedKmph(speedResult.speedKmph());
        location.setMovementType(speedResult.movementType());

        OfficeGeofenceService.OfficeResult officeResult = officeGeofenceService.evaluateAndStore(
                user, request.getLatitude(), request.getLongitude(), null);
        location.setInsideOffice(officeResult.insideOffice());
        location.setOfficeName(officeResult.officeName());

        location.setAddress(reverseGeocodingService.reverseGeocode(request.getLatitude(), request.getLongitude()));

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

        // Detect nearby educational institutions and send notifications.
        // The radius is whatever is currently selected on the employee's
        // Radius dropdown (LocationRequest#getRadiusMeters(), sent by the
        // dashboard whenever "Show Nearby Colleges" is on) so the
        // background notification job always matches the radius used for
        // the map/circle/nearby search - never a hardcoded default.
        try {
            List<com.employeetracker.dto.NearbyPlaceDto> newNearbyPlaces = 
                nearbyPlaceService.processLocationUpdate(userId, request.getLatitude(), request.getLongitude(),
                        request.getRadiusMeters());
            
            // Send notifications for newly detected places
            for (com.employeetracker.dto.NearbyPlaceDto place : newNearbyPlaces) {
                if (!place.isNotified()) {
                   // Send notification to employee
notificationService.createNearbyCollegeNotification(
    userId,
    place.getPlaceName(),
    place.getDistance().doubleValue(),
    place.getLatitude().doubleValue(),
    place.getLongitude().doubleValue()
);

// Send notification to admins
notificationService.createAdminNearbyCollegeNotification(
    user.getName(),
    place.getPlaceName(),
    place.getDistance().doubleValue()
);

// Mark as notified
nearbyPlaceService.markAsNotified(place.getPlaceId());
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the location update
            logger.error("Error processing nearby places: {}", e.getMessage());
        }

        return buildLocationResponse(user, saved);
    }

    public LocationResponse getCurrentLocation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Optional<EmployeeLocation> location = locationRepository.findTopByUserIdOrderByLocationTimeDesc(userId);

        return buildLocationResponse(user, location.orElse(null));
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


    /**
     * Updates the employee's LastSeenTime to now. Called by the dashboard's
     * heartbeat (every 30s while the dashboard is open), regardless of
     * whether Tracking is currently ON or OFF, so the Admin Dashboard's
     * Online/Offline status (see determineTrackingStatus) accurately
     * reflects whether the browser/tab is actually still open and connected.
     */
    @Transactional
    public void recordHeartbeat(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLastSeenTime(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<StopDto> getTodayStops(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime start = DateTimeUtil.startOfToday();
        LocalDateTime end = DateTimeUtil.endOfToday();

        List<StopDto> autoStops = stopRepository.findUserStopsInRange(userId, start, end).stream()
                .map(stop -> mapToStopDto(user, stop))
                .filter(this::meetsMinimumStopDuration)
                .collect(Collectors.toList());

        // Include today's manually added stops ("+ Add Stop" popup) so they
        // show up immediately in the employee's own Stop History card, the
        // same way they are already merged into getStopsForRange() for
        // reports/admin. Manual stops are always shown regardless of
        // duration - the minimum-duration noise filter above only applies
        // to automatic GPS-idle detection.
        List<StopDto> manualStops = trackingStopRepository.search(userId, start, end, null)
                .stream()
                .map(stop -> mapManualStopToDto(user, stop))
                .collect(Collectors.toList());

        List<StopDto> combined = new ArrayList<>(autoStops);
        combined.addAll(manualStops);
        combined.sort(Comparator.comparing(StopDto::getStartTime).reversed());

        return combined;
    }

    @Transactional
    public List<StopDto> getStopsForRange(Long userId, LocalDate fromDate, LocalDate toDate) {

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    LocalDateTime start = DateTimeUtil.startOfDay(fromDate);
    LocalDateTime end = DateTimeUtil.endOfDay(toDate);

    List<StopDto> autoStops = stopRepository.findUserStopsInRange(userId, start, end)
            .stream()
            .map(stop -> mapToStopDto(user, stop))
            .filter(this::meetsMinimumStopDuration)
            .collect(Collectors.toList());

    // Root cause of Manual Stops missing from Reports / Stop History / Report
    // Summary / exports: this method only ever read from EmployeeStop (the
    // automatic GPS-idle stop-detection table). Manual "Tracking OFF" stops
    // are saved correctly by TrackingStopService into the separate
    // TrackingStops table, but were never merged in here. Every manual stop
    // in range must be included regardless of duration (the noise filter
    // above only applies to automatic GPS-idle detection) and regardless of
    // whether it is still active/ongoing (EndTime IS NULL).
    List<StopDto> manualStops = trackingStopRepository.search(userId, start, end, null)
            .stream()
            .map(stop -> mapManualStopToDto(user, stop))
            .collect(Collectors.toList());

    List<StopDto> combined = new ArrayList<>(autoStops);
    combined.addAll(manualStops);
    // startTime is formatted "yyyy-MM-dd HH:mm:ss", a fixed-width format
    // where lexical ordering matches chronological ordering, so sorting the
    // combined list this way preserves the original most-recent-first order.
    combined.sort(Comparator.comparing(StopDto::getStartTime).reversed());

    return combined;
}

    private StopDto mapManualStopToDto(User user, TrackingStop stop) {
        StopDto dto = new StopDto();
        dto.setStopId(stop.getStopId());
        dto.setUserId(stop.getUserId());
        dto.setEmployeeName(user.getName());
        dto.setEmployeeId(user.getEmployeeId());
        if (stop.getLatitude() != null) {
            dto.setLatitude(stop.getLatitude().doubleValue());
        }
        if (stop.getLongitude() != null) {
            dto.setLongitude(stop.getLongitude().doubleValue());
        }
        dto.setStartTime(stop.getStartTime().format(FORMATTER));
        if (stop.getEndTime() != null) {
            dto.setEndTime(stop.getEndTime().format(FORMATTER));
        }
        if (stop.getDurationMinutes() != null) {
            int seconds = stop.getDurationMinutes() * 60;
            dto.setDurationSeconds(seconds);
            dto.setDuration(DateTimeUtil.formatDuration(seconds));
        } else {
            int seconds = (int) java.time.Duration.between(stop.getStartTime(), LocalDateTime.now()).getSeconds();
            dto.setDurationSeconds(seconds);
            dto.setDuration(DateTimeUtil.formatDuration(seconds) + " (ongoing)");
        }
        dto.setAddress(stop.getAddress() != null && !stop.getAddress().isBlank() ? stop.getAddress() : "Address unavailable");
        if (stop.getLatitude() != null && stop.getLongitude() != null) {
            dto.setGoogleMapsUrl("https://www.google.com/maps?q=" + stop.getLatitude().toPlainString()
                    + "," + stop.getLongitude().toPlainString());
        }
        if (stop.getStopReason() != null) {
            dto.setStopReason(stop.getStopReason().name());
            dto.setStopReasonLabel(stop.getStopReason().getLabel());
        }
        dto.setRemarks(stop.getRemarks());
        dto.setManual(true);
        return dto;
    }

    /**
     * Requirement: very small stops (a few seconds, 30 seconds, 2 minutes, etc.)
     * should never be recorded as a "stop". A stop is only counted once the
     * employee has remained inside the stop radius continuously for at least
     * tracking.stop.duration-minutes (configurable via application.properties,
     * never hardcoded). Applies everywhere stops are read: Employee Report,
     * Admin Report, Stop History, Excel Export, and PDF Export.
     */
    private boolean meetsMinimumStopDuration(StopDto stop) {
        long minimumSeconds = trackingProperties.getStopDurationMinutes() * 60L;
        return stop.getDurationSeconds() >= minimumSeconds;
    }

    public List<ActivityDto> getTodayActivities(Long userId) {
        LocalDateTime start = DateTimeUtil.startOfToday();
        LocalDateTime end = DateTimeUtil.endOfToday();

        return activityRepository.findTodayActivities(userId, start, end).stream()
                .map(this::mapToActivityDto)
                .collect(Collectors.toList());
    }
    

    @Transactional
    public LocationResponse setTrackingEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.EMPLOYEE) {
            throw new BadRequestException("Only employees can change tracking state");
        }

        if (!user.isLoggedIn()) {
            user.setTrackingEnabled(false);
            userRepository.save(user);
            return buildLocationResponse(user, getLatestLocation(userId).orElse(null));
        }

        if (user.isTrackingEnabled() != enabled) {
            user.setTrackingEnabled(enabled);
            userRepository.save(user);
            activityService.logActivity(
                    userId,
                    enabled ? ActivityType.TRACKING_ENABLED : ActivityType.TRACKING_DISABLED,
                    enabled ? "Tracking enabled" : "Tracking disabled",
                    null,
                    null,
                    null
            );

            if (!enabled) {
                // Close any open stop, but never delete location history: determineTrackingStatus()
                // already returns OFFLINE purely from user.isTrackingEnabled(), so deleting the
                // latest EmployeeLocation row served no purpose here other than destroying data
                // needed by reports, the route map, and location-update counts.
                stopDetectionService.closeOpenStopForUser(userId, LocalDateTime.now());
            } else {
                // Tracking Resume requirement: automatically close the employee's active
                // manual Stop Reason record (if any) and record its Stop End Time/Duration.
                trackingStopService.closeActiveStopIfExists(userId, LocalDateTime.now());
            }
        }

        return buildLocationResponse(user, getLatestLocation(userId).orElse(null));
    }

    // Heartbeat-based Online/Offline fix: if the dashboard has not sent a
    // heartbeat (see /api/location/heartbeat) within this window, the
    // employee is treated as Offline even though IsLoggedIn/TrackingEnabled
    // may still say otherwise - this is exactly the case when the browser
    // was closed or the connection was lost without a clean logout.
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 60;

    public TrackingStatus determineTrackingStatus(User user, EmployeeLocation location) {
        if (user == null || !user.isLoggedIn() || !user.isTrackingEnabled()) {
            return TrackingStatus.OFFLINE;
        }

        LocalDateTime lastSeen = user.getLastSeenTime() != null ? user.getLastSeenTime() : user.getLastLoginTime();
        if (lastSeen == null
                || java.time.Duration.between(lastSeen, LocalDateTime.now()).getSeconds() > HEARTBEAT_TIMEOUT_SECONDS) {
            return TrackingStatus.OFFLINE;
        }

        if (stopDetectionService.isCurrentlyStopped(user.getUserId())) {
            return TrackingStatus.STOPPED;
        }

        if (location == null) {
            return TrackingStatus.ONLINE;
        }

        long minutesSinceUpdate = java.time.Duration.between(location.getLocationTime(), LocalDateTime.now()).toMinutes();
        if (minutesSinceUpdate > trackingProperties.getOnlineThresholdMinutes()) {
            return TrackingStatus.ONLINE;
        }

        return TrackingStatus.MOVING;
    }

    private LocationResponse buildLocationResponse(User user, EmployeeLocation location) {
        if (location != null && (location.getAddress() == null || location.getAddress().isBlank())) {
            String resolved = reverseGeocodingService.reverseGeocode(location.getLatitude(), location.getLongitude());
            if (resolved != null && !resolved.isBlank()) {
                location.setAddress(resolved);
                locationRepository.save(location);
            } else {
                location.setAddress("Address unavailable");
            }
        }

        LocationResponse response = mapToLocationResponse(user, location);
        response.setTodayDistanceKm(distanceCalculationService.calculateTodayDistanceKm(user.getUserId()));
        response.setStatus(determineTrackingStatus(user, location).name());
        response.setTrackingEnabled(user.isLoggedIn() && user.isTrackingEnabled());
        return response;
    }

    private LocationResponse mapToLocationResponse(User user, EmployeeLocation location) {
        LocationResponse response = new LocationResponse();
        response.setUserId(user.getUserId());
        response.setEmployeeName(user.getName());
        if (location != null) {
            response.setLocationId(location.getLocationId());
            response.setLatitude(location.getLatitude());
            response.setLongitude(location.getLongitude());
            response.setAccuracy(location.getAccuracy());
            response.setLocationTime(location.getLocationTime().format(FORMATTER));
            response.setInsideOffice(location.isInsideOffice());
            response.setOfficeName(location.getOfficeName());
            response.setAddress(location.getAddress());
            if (location.getSpeedKmph() != null) {
                response.setSpeedKmph(location.getSpeedKmph().doubleValue());
            }
            response.setMovementType(location.getMovementType());
            response.setLastSeen(location.getLocationTime().format(FORMATTER));
        }
        return response;
    }

    private Optional<EmployeeLocation> getLatestLocation(Long userId) {
        return locationRepository.findTopByUserIdOrderByLocationTimeDesc(userId);
    }

    private StopDto mapToStopDto(User user, EmployeeStop stop) {
        StopDto dto = new StopDto();
        dto.setStopId(stop.getStopId());
        dto.setUserId(stop.getUserId());
        dto.setEmployeeName(user.getName());
        dto.setEmployeeId(user.getEmployeeId());
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
        dto.setAddress(resolveStopAddress(stop));
        dto.setGoogleMapsUrl("https://www.google.com/maps?q=" + stop.getLatitude().toPlainString()
                + "," + stop.getLongitude().toPlainString());
        return dto;
    }

    /**
     * Returns the human readable address for a stop, resolving it via reverse
     * geocoding and caching the result on the EmployeeStop row the first time
     * it is requested. Subsequent report views for the same stop reuse the
     * cached value instead of calling the geocoding provider again. If the
     * lookup cannot be resolved (e.g. no network), a generic fallback is
     * returned without being persisted, so the next request can retry.
     */
    private String resolveStopAddress(EmployeeStop stop) {
        if (stop.getAddress() != null && !stop.getAddress().isBlank()) {
            return stop.getAddress();
        }

        String resolved = reverseGeocodingService.reverseGeocode(stop.getLatitude(), stop.getLongitude());
        if (resolved != null && !resolved.isBlank()) {
            stop.setAddress(resolved);
            stopRepository.save(stop);
            return resolved;
        }

        return "Address unavailable";
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