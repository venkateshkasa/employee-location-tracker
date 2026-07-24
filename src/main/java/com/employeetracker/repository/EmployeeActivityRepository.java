package com.employeetracker.repository;

import com.employeetracker.entity.EmployeeActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmployeeActivityRepository extends JpaRepository<EmployeeActivity, Long> {

    List<EmployeeActivity> findByUserIdAndActivityTimeBetweenOrderByActivityTimeDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM EmployeeActivity a WHERE a.userId = :userId AND a.activityTime >= :start AND a.activityTime < :end ORDER BY a.activityTime DESC")
    List<EmployeeActivity> findTodayActivities(@Param("userId") Long userId,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    List<EmployeeActivity> findTop20ByActivityTypeInOrderByActivityTimeDesc(List<com.employeetracker.entity.ActivityType> activityTypes);

    // Used by DistanceCalculationService to find "tracking session start"
    // boundaries (LOGIN / TRACKING_ENABLED) so that distance is never summed
    // across a login or a Tracking OFF -> ON transition - see
    // DistanceCalculationService#calculateDistanceKm for details.
    List<EmployeeActivity> findByUserIdAndActivityTypeInAndActivityTimeBetweenOrderByActivityTimeAsc(
            Long userId, List<com.employeetracker.entity.ActivityType> activityTypes,
            LocalDateTime start, LocalDateTime end);
}