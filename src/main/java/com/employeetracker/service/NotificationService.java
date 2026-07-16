package com.employeetracker.service;

import com.employeetracker.dto.NotificationDto;
import com.employeetracker.entity.Notification;
import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.repository.NotificationRepository;
import com.employeetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing notifications for employees and admins.
 * Handles creation, retrieval, and marking notifications as read.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a notification for a specific user
     * 
     * @param userId User ID to send notification to
     * @param title Notification title
     * @param message Notification message
     * @param notificationType Type of notification
     * @return Created notification DTO
     */
    @Transactional
    public NotificationDto createNotification(Long userId,
                   String title,
                   String message,
                   Notification.NotificationType notificationType,
                   Double latitude,
                   Double longitude,
                   String placeName) {
        User user = userRepository.findById(userId).orElse(null);
        List<Notification> recentNotifications =
        notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

boolean alreadyExists = recentNotifications.stream().anyMatch(n ->
        n.getTitle().equals(title) &&
n.getMessage().equals(message) &&
placeName.equals(n.getPlaceName())
);

if (alreadyExists) {
    logger.debug("Duplicate notification skipped for user {}", userId);
    return null;
}
        if (user == null) {
            logger.warn("Cannot create notification for non-existent user: {}", userId);
            return null;
        }

        Notification notification = new Notification();
        notification.setLatitude(latitude);
notification.setLongitude(longitude);
notification.setPlaceName(placeName);
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(notificationType);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        logger.info("Created notification for user {}: {}", userId, title);

        return mapToDto(saved);
    }

    /**
     * Create a notification for all admins
     * 
     * @param title Notification title
     * @param message Notification message
     * @param notificationType Type of notification
     * @return List of created notification DTOs
     */
    @Transactional
    
public List<NotificationDto> createAdminNotification(
        String title,
        String message,
        Notification.NotificationType notificationType) {

    List<User> admins = userRepository.findByRole(UserRole.ADMIN);

    return admins.stream()
            .map(admin -> createNotification(
                    admin.getUserId(),
                    title,
                    message,
                    notificationType,
                    null,
                    null,
                    null
            ))
            .filter(dto -> dto != null)
            .collect(Collectors.toList());
}
    /**
     * Create notification for nearby college detection for employee
     * 
     * @param userId Employee user ID
     * @param placeName Name of the nearby place
     * @param distance Distance to the place in meters
     * @return Created notification DTO
     */
    @Transactional
    public NotificationDto createNearbyCollegeNotification(Long userId,
                                String placeName,
                                double distance,
                                Double latitude,
                                Double longitude) {
        String distanceKm = String.format("%.1f", distance / 1000.0);
        String title = "📍 Nearby University";

String message = String.format(
        "You are within %s KM of %s.",
        distanceKm,
        placeName
);
        
return createNotification(
    userId,
    title,
    message,
    Notification.NotificationType.NEARBY_COLLEGE,
    latitude,
    longitude,
    placeName
);    }

    /**
     * Create notification for admin about employee entering nearby college
     * 
     * @param employeeName Name of the employee
     * @param placeName Name of the nearby place
     * @param distance Distance to the place in meters
     * @return List of created notification DTOs for all admins
     */
    @Transactional
    public List<NotificationDto> createAdminNearbyCollegeNotification(String employeeName, 
                                                                        String placeName, 
                                                                        double distance) {
        String distanceKm = String.format("%.1f", distance / 1000.0);
        String title = "📍 Employee Nearby University";

String message = String.format(
        "%s entered within %s KM of %s.",
        employeeName,
        distanceKm,
        placeName
);
        return createAdminNotification(title, message, Notification.NotificationType.NEARBY_COLLEGE);
    }

    /**
     * Get notifications for a specific user
     * 
     * @param userId User ID
     * @return List of notification DTOs
     */
    public List<NotificationDto> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get unread notifications for a specific user
     * 
     * @param userId User ID
     * @return List of unread notification DTOs
     */
    public List<NotificationDto> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all notifications (for admin)
     * 
     * @return List of all notification DTOs
     */
    public List<NotificationDto> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get recent notifications for admin dashboard
     * 
     * @param since Time threshold
     * @return List of recent notification DTOs
     */
    public List<NotificationDto> getRecentNotifications(LocalDateTime since) {
        return notificationRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since)
            .stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Mark a notification as read
     * 
     * @param notificationId Notification ID
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
            logger.debug("Marked notification {} as read", notificationId);
        });
    }

    /**
     * Mark all notifications for a user as read
     * 
     * @param userId User ID
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = 
            notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        
        for (Notification notification : unreadNotifications) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        
        logger.info("Marked {} notifications as read for user {}", unreadNotifications.size(), userId);
    }

    /**
     * Get count of unread notifications for a user
     * 
     * @param userId User ID
     * @return Count of unread notifications
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Clean up old notifications (can be called periodically)
     * 
     * @param daysToKeep Number of days to keep
     */
    @Transactional
    public void cleanupOldNotifications(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        notificationRepository.deleteByCreatedAtBefore(cutoff);
        logger.info("Cleaned up notifications older than {} days", daysToKeep);
    }

    /**
     * Map entity to DTO
     */
    private NotificationDto mapToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setNotificationId(notification.getNotificationId());
        dto.setUserId(notification.getUserId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setNotificationType(notification.getNotificationType().name());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setLatitude(notification.getLatitude());
dto.setLongitude(notification.getLongitude());
dto.setPlaceName(notification.getPlaceName());
        return dto;
    }
}
