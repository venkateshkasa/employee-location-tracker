package com.employeetracker.controller;

import com.employeetracker.dto.ApiResponse;
import com.employeetracker.dto.NotificationDto;
import com.employeetracker.entity.User;
import com.employeetracker.service.AuthService;
import com.employeetracker.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for notifications API
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    /**
     * Get all notifications for the logged-in user
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications() {
        User user = authService.getCurrentUserEntity();
        List<NotificationDto> notifications = notificationService.getUserNotifications(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    /**
     * Get unread notifications for the logged-in user
     */
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getUnreadNotifications() {
        User user = authService.getCurrentUserEntity();
        List<NotificationDto> notifications = notificationService.getUnreadNotifications(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    /**
     * Get count of unread notifications
     */
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount() {
        User user = authService.getCurrentUserEntity();
        long count = notificationService.getUnreadCount(user.getUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Mark a notification as read
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<String>> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    /**
     * Mark all notifications as read for the logged-in user
     */
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<String>> markAllAsRead() {
        User user = authService.getCurrentUserEntity();
        notificationService.markAllAsRead(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
    }
}
