package com.employeetracker.service;

import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.repository.EmployeeActivityRepository;
import com.employeetracker.repository.EmployeeLocationRepository;
import com.employeetracker.util.DateTimeUtil;
import com.employeetracker.util.HaversineUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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

    /**
     * Maximum realistic distance (in meters) between two consecutive GPS
     * updates. A single jump bigger than this (an employee logging in from a
     * completely different city than their last known point, a GPS glitch,
     * etc.) can never be a real, continuously-tracked movement, so it is
     * never added to the travelled distance - see class-level docs below.
     */
    private static final double MAX_REALISTIC_JUMP_METERS = 20000.0;

    /**
     * Maximum realistic travel speed (km/h) between two consecutive GPS
     * updates. Even a jump smaller than MAX_REALISTIC_JUMP_METERS can still
     * be an impossible/glitched movement if it implies a speed no normal
     * commute could reach (e.g. 15 km covered in 10 seconds). Any segment
     * implying a faster speed than this is treated the same way as a GPS
     * jump: never added to the travelled distance.
     */
    private static final double MAX_REALISTIC_SPEED_KMPH = 200.0;

    /**
     * Activity types that mark the start of a brand new GPS tracking
     * session: a fresh Login, or Tracking being switched back ON after being
     * OFF. Any location recorded at/after one of these events must never
     * have its distance computed against a location from *before* the
     * event - see calculateDistanceKm().
     */
    private static final List<ActivityType> SESSION_START_TYPES =
            Arrays.asList(ActivityType.LOGIN, ActivityType.TRACKING_ENABLED);

    private final EmployeeLocationRepository locationRepository;
    private final EmployeeActivityRepository activityRepository;

    public DistanceCalculationService(EmployeeLocationRepository locationRepository,
                                       EmployeeActivityRepository activityRepository) {
        this.locationRepository = locationRepository;
        this.activityRepository = activityRepository;
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

    /**
     * Cumulative distance travelled by the employee between fromDate and
     * toDate (inclusive), summing the distance between consecutive GPS
     * location updates - but ONLY while those updates belong to the same,
     * continuously-tracked session. The previous session's last known
     * location is NEVER used as the starting point for a new session, so:
     *
     *  - New Login: the first GPS fix after logging in always starts a new
     *    baseline; distance is never computed from wherever the employee
     *    happened to log out last (possibly a different city entirely).
     *  - Tracking OFF -> ON: same as above - re-enabling tracking starts a
     *    fresh baseline, ignoring whatever movement happened while tracking
     *    was off.
     *  - New Day: handled naturally, since only rows within [fromDate,
     *    toDate] are ever looked at.
     *  - Unrealistic GPS jump: a single segment bigger than
     *    MAX_REALISTIC_JUMP_METERS, or implying a speed faster than
     *    MAX_REALISTIC_SPEED_KMPH, is treated as a GPS glitch - it is never
     *    added to the total, and the baseline resets to the new point (the
     *    same way a session boundary does) rather than keeping the stale,
     *    likely-wrong previous point around for the next comparison.
     */
    public double calculateDistanceKm(Long userId, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime start = DateTimeUtil.startOfDay(fromDate);
        LocalDateTime end = DateTimeUtil.endOfDay(toDate);
        List<EmployeeLocation> locations = locationRepository.findTodayLocations(userId, start, end);

        if (locations.size() < 2) {
            return 0.0;
        }

        List<EmployeeActivity> sessionStarts = activityRepository
                .findByUserIdAndActivityTypeInAndActivityTimeBetweenOrderByActivityTimeAsc(
                        userId, SESSION_START_TYPES, start, end);

        double totalMeters = 0.0;
        // Compare each new point against the last point that was actually
        // counted as movement (not just the immediately previous row), so
        // small back-and-forth GPS jitter under MIN_MOVEMENT_METERS never
        // accumulates into a fake "distance travelled" total.
        EmployeeLocation lastCounted = locations.get(0);
        for (int i = 1; i < locations.size(); i++) {
            EmployeeLocation curr = locations.get(i);

            // A Login or a Tracking OFF -> ON event happened between the last
            // counted point and this one: whatever the employee's location
            // was before that event is irrelevant to the new session. Reset
            // the baseline to the current point without adding any distance
            // for this segment.
            if (hasSessionStartBetween(sessionStarts, lastCounted.getLocationTime(), curr.getLocationTime())) {
                lastCounted = curr;
                continue;
            }

            double segmentMeters = HaversineUtil.calculateDistanceMeters(
                    lastCounted.getLatitude(), lastCounted.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );

            if (segmentMeters < MIN_MOVEMENT_METERS) {
                continue;
            }

            if (isUnrealisticJump(segmentMeters, lastCounted.getLocationTime(), curr.getLocationTime())) {
                // GPS glitch / impossible jump: don't count it, but do treat
                // the new point as the fresh baseline so a single bad fix
                // doesn't keep poisoning every future comparison.
                lastCounted = curr;
                continue;
            }

            totalMeters += segmentMeters;
            lastCounted = curr;
        }

        return HaversineUtil.metersToKilometers(totalMeters);
    }

    private boolean hasSessionStartBetween(List<EmployeeActivity> sessionStarts,
                                            LocalDateTime afterExclusive,
                                            LocalDateTime upToInclusive) {
        for (EmployeeActivity activity : sessionStarts) {
            LocalDateTime activityTime = activity.getActivityTime();
            if (activityTime.isAfter(afterExclusive) && !activityTime.isAfter(upToInclusive)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnrealisticJump(double segmentMeters, LocalDateTime fromTime, LocalDateTime toTime) {
        if (segmentMeters > MAX_REALISTIC_JUMP_METERS) {
            return true;
        }

        long elapsedSeconds = java.time.Duration.between(fromTime, toTime).getSeconds();
        if (elapsedSeconds <= 0) {
            // Same/near-identical timestamps but far apart in space - can't
            // be a real, continuously-tracked movement.
            return segmentMeters >= MIN_MOVEMENT_METERS;
        }

        double impliedSpeedKmph = (segmentMeters / 1000.0) / (elapsedSeconds / 3600.0);
        return impliedSpeedKmph > MAX_REALISTIC_SPEED_KMPH;
    }
}