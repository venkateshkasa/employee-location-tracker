package com.employeetracker.service;

import com.employeetracker.dto.TrackingStopDto;
import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.StopReason;
import com.employeetracker.entity.TrackingStop;
import com.employeetracker.entity.User;
import com.employeetracker.exception.BadRequestException;
import com.employeetracker.exception.ResourceNotFoundException;
import com.employeetracker.repository.TrackingStopRepository;
import com.employeetracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles the manual "Add Stop" feature: from the Stop History card on the
 * employee dashboard, clicking "+ Add Stop" opens a popup where the
 * employee records a break/stop reason together with an explicit Start
 * Time and End Time. The current GPS location is captured automatically.
 * Saving an "Add Stop" record is completely independent of Tracking ON/OFF
 * - it never changes the tracking state.
 * <p>
 * This is entirely separate from {@link StopDetectionService} /
 * {@link com.employeetracker.entity.EmployeeStop}, which is the existing
 * automatic GPS-idle stop detection feature and is never touched here.
 */
@Service
public class TrackingStopService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TrackingStopRepository trackingStopRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final ReverseGeocodingService reverseGeocodingService;

    public TrackingStopService(TrackingStopRepository trackingStopRepository,
                               UserRepository userRepository,
                               ActivityService activityService,
                               ReverseGeocodingService reverseGeocodingService) {
        this.trackingStopRepository = trackingStopRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.reverseGeocodingService = reverseGeocodingService;
    }

    private static final DateTimeFormatter TIME_INPUT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Validates and saves a manual "Add Stop" record for the employee.
     * Unlike the old Tracking-OFF-triggered flow, this creates a single
     * complete record in one call - Start Time and End Time are both
     * supplied by the employee (combined with today's date), so there is
     * no "active"/open stop concept here. Never touches Tracking ON/OFF
     * state.
     */
    @Transactional
    public TrackingStopDto addStop(Long userId, String stopReasonRaw, String remarks,
                                   String startTimeRaw, String endTimeRaw,
                                   Double latitude, Double longitude) {
        if (stopReasonRaw == null || stopReasonRaw.isBlank()) {
            throw new BadRequestException("Stop reason is required");
        }

        StopReason stopReason;
        try {
            stopReason = StopReason.valueOf(stopReasonRaw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid stop reason");
        }

        if (stopReason == StopReason.OTHER && (remarks == null || remarks.isBlank())) {
            throw new BadRequestException("Remarks is required when Stop Reason is Other");
        }

        if (startTimeRaw == null || startTimeRaw.isBlank()) {
            throw new BadRequestException("Start time is required");
        }
        if (endTimeRaw == null || endTimeRaw.isBlank()) {
            throw new BadRequestException("End time is required");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startTime = parseTimeOfDay(today, startTimeRaw, "Start time");
        LocalDateTime endTime = parseTimeOfDay(today, endTimeRaw, "End time");

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("End Time must be greater than Start Time");
        }

        if (latitude == null || longitude == null) {
            throw new BadRequestException("Current GPS location is required to add a stop");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TrackingStop stop = new TrackingStop();
        stop.setUserId(userId);
        stop.setStopReason(stopReason);
        stop.setRemarks(remarks != null && !remarks.isBlank() ? remarks.trim() : null);
        BigDecimal lat = BigDecimal.valueOf(latitude);
        BigDecimal lng = BigDecimal.valueOf(longitude);
        stop.setLatitude(lat);
        stop.setLongitude(lng);
        stop.setAddress(reverseGeocodingService.reverseGeocode(lat, lng));
        stop.setStartTime(startTime);
        stop.setEndTime(endTime);
        stop.setDurationMinutes((int) Duration.between(startTime, endTime).toMinutes());
        stop.setCreatedAt(LocalDateTime.now());

        TrackingStop saved = trackingStopRepository.save(stop);

        activityService.logActivity(
                userId,
                ActivityType.MANUAL_STOP_ADDED,
                "Stop added: " + stopReason.getLabel(),
                lat,
                lng,
                saved.getStopId()
        );

        return mapToDto(saved, user);
    }

    /** Parses an "HH:mm" time-of-day string against today's date. */
    private LocalDateTime parseTimeOfDay(LocalDate date, String raw, String fieldLabel) {
        try {
            LocalTime time = LocalTime.parse(raw.trim(), TIME_INPUT_FORMATTER);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(fieldLabel + " must be a valid time (HH:mm)");
        }
    }

    /**
     * Closes the employee's currently active manual stop (if any) when
     * tracking is turned back ON, recording the end time and duration.
     * No-op if there is no active manual stop, so this is always safe to
     * call from the Tracking ON path.
     */
    @Transactional
    public void closeActiveStopIfExists(Long userId, LocalDateTime endTime) {
        Optional<TrackingStop> activeStop =
                trackingStopRepository.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId);
        if (activeStop.isEmpty()) {
            return;
        }

        TrackingStop stop = activeStop.get();
        stop.setEndTime(endTime);
        int minutes = (int) Duration.between(stop.getStartTime(), endTime).toMinutes();
        stop.setDurationMinutes(Math.max(minutes, 0));
        TrackingStop saved = trackingStopRepository.save(stop);

        activityService.logActivity(
                userId,
                ActivityType.MANUAL_STOP_ENDED,
                "Tracking resumed after: " + saved.getStopReason().getLabel(),
                saved.getLatitude(),
                saved.getLongitude(),
                saved.getStopId()
        );
    }

    /**
     * Admin "Stop History" report search with optional Employee, Date From,
     * Date To, and Stop Reason filters.
     */
    @Transactional(readOnly = true)
    public List<TrackingStopDto> searchStopHistory(Long userId, LocalDate fromDate, LocalDate toDate,
                                                   String stopReasonRaw) {
        LocalDateTime start = fromDate != null ? fromDate.atStartOfDay() : LocalDate.now().atStartOfDay();
        LocalDateTime end = toDate != null ? toDate.atTime(23, 59, 59) : LocalDate.now().atTime(23, 59, 59);

        StopReason stopReason = null;
        if (stopReasonRaw != null && !stopReasonRaw.isBlank()) {
            try {
                stopReason = StopReason.valueOf(stopReasonRaw.trim());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid stop reason filter");
            }
        }

        List<TrackingStop> stops = trackingStopRepository.search(userId, start, end, stopReason);

        Map<Long, User> userCache = new HashMap<>();
        return stops.stream()
                .map(stop -> {
                    User user = userCache.computeIfAbsent(stop.getUserId(), id ->
                            userRepository.findById(id).orElse(null));
                    return mapToDto(stop, user);
                })
                .collect(Collectors.toList());
    }

    private TrackingStopDto mapToDto(TrackingStop stop, User user) {
        TrackingStopDto dto = new TrackingStopDto();
        dto.setStopId(stop.getStopId());
        dto.setUserId(stop.getUserId());
        dto.setEmployeeId(user != null ? user.getEmployeeId() : null);
        dto.setEmployeeName(user != null ? user.getName() : "Unknown");
        dto.setDate(stop.getStartTime().format(DATE_FORMATTER));
        dto.setStopReason(stop.getStopReason().name());
        dto.setStopReasonLabel(stop.getStopReason().getLabel());
        dto.setRemarks(stop.getRemarks());
        dto.setStartTime(stop.getStartTime().format(FORMATTER));
        dto.setActive(stop.getEndTime() == null);
        if (stop.getEndTime() != null) {
            dto.setEndTime(stop.getEndTime().format(FORMATTER));
            dto.setDuration(formatMinutes(stop.getDurationMinutes()));
        } else {
            int minutes = (int) Duration.between(stop.getStartTime(), LocalDateTime.now()).toMinutes();
            dto.setDuration(formatMinutes(minutes) + " (ongoing)");
        }
        dto.setAddress(stop.getAddress() != null ? stop.getAddress() : "Address unavailable");
        if (stop.getLatitude() != null) {
            dto.setLatitude(stop.getLatitude().doubleValue());
        }
        if (stop.getLongitude() != null) {
            dto.setLongitude(stop.getLongitude().doubleValue());
        }
        return dto;
    }

    private String formatMinutes(Integer minutes) {
        if (minutes == null) {
            return "-";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}
