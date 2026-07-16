package com.employeetracker.repository;

import com.employeetracker.entity.NearbyPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for NearbyPlace entity operations
 */
public interface NearbyPlaceRepository extends JpaRepository<NearbyPlace, Long> {

    /**
     * Find active nearby places for a user (places that have not been left yet)
     */
    List<NearbyPlace> findByUserIdAndLeftTimeIsNullOrderByEnteredTimeDesc(Long userId);

    /**
     * Find all nearby places for a user within a time range
     */
    List<NearbyPlace> findByUserIdAndEnteredTimeBetweenOrderByEnteredTimeDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * Find a nearby place by user, place name, and location (to avoid duplicates)
     */
    @Query("SELECT np FROM NearbyPlace np WHERE np.userId = :userId AND np.placeName = :placeName " +
           "AND np.leftTime IS NULL")
    Optional<NearbyPlace> findActivePlaceByName(@Param("userId") Long userId, 
                                                @Param("placeName") String placeName);

    /**
     * Find all active nearby places across all users (for admin dashboard)
     */
    List<NearbyPlace> findByLeftTimeIsNullOrderByEnteredTimeDesc();

    /**
     * Find nearby places for a specific user that are currently active
     */
    @Query("SELECT np FROM NearbyPlace np WHERE np.userId = :userId AND np.leftTime IS NULL " +
           "AND np.enteredTime >= :since")
    List<NearbyPlace> findActivePlacesSince(@Param("userId") Long userId, 
                                            @Param("since") LocalDateTime since);

    /**
     * Delete old nearby places records (cleanup)
     */
    void deleteByEnteredTimeBefore(LocalDateTime before);
}
