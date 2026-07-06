package com.employeetracker.repository;

import com.employeetracker.entity.EmployeeLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmployeeLocationRepository extends JpaRepository<EmployeeLocation, Long> {

    Optional<EmployeeLocation> findTopByUserIdOrderByLocationTimeDesc(Long userId);

    List<EmployeeLocation> findByUserIdAndLocationTimeBetweenOrderByLocationTimeAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    List<EmployeeLocation> findByLocationTimeBetweenOrderByLocationTimeDesc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT l FROM EmployeeLocation l WHERE l.userId = :userId AND l.locationTime >= :start AND l.locationTime < :end ORDER BY l.locationTime ASC")
    List<EmployeeLocation> findTodayLocations(@Param("userId") Long userId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT l FROM EmployeeLocation l WHERE l.locationTime >= :start AND l.locationTime < :end ORDER BY l.locationTime DESC")
    List<EmployeeLocation> findAllTodayLocations(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);
}
