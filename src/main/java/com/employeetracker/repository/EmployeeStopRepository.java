package com.employeetracker.repository;

import com.employeetracker.entity.EmployeeStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmployeeStopRepository extends JpaRepository<EmployeeStop, Long> {

    Optional<EmployeeStop> findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(Long userId);

    List<EmployeeStop> findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s FROM EmployeeStop s WHERE s.startTime >= :start AND s.startTime < :end ORDER BY s.startTime DESC")
    List<EmployeeStop> findAllStopsInRange(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    @Query("SELECT s FROM EmployeeStop s WHERE s.userId = :userId AND s.startTime >= :start AND s.startTime < :end ORDER BY s.startTime DESC")
    List<EmployeeStop> findUserStopsInRange(@Param("userId") Long userId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
