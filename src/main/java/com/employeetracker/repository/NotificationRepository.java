package com.employeetracker.repository;

import com.employeetracker.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Notification entity operations
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications for a specific user
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find unread notifications for a specific user
     */
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    /**
     * Find all notifications (for admin)
     */
    List<Notification> findAllByOrderByCreatedAtDesc();

    /**
     * Find notifications created after a specific time
     */
    List<Notification> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    /**
     * Count unread notifications for a user
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Delete old notifications (cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
