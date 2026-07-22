package com.employeetracker.repository;

import com.employeetracker.entity.StopReason;
import com.employeetracker.entity.TrackingStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrackingStopRepository extends JpaRepository<TrackingStop, Long> {

    Optional<TrackingStop> findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(Long userId);

    @Query("SELECT t FROM TrackingStop t WHERE " +
            "(:userId IS NULL OR t.userId = :userId) AND " +
            "t.startTime >= :start AND t.startTime <= :end AND " +
            "(:stopReason IS NULL OR t.stopReason = :stopReason) " +
            "ORDER BY t.startTime DESC")
    List<TrackingStop> search(@Param("userId") Long userId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end,
                               @Param("stopReason") StopReason stopReason);
}
