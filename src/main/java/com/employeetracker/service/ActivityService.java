package com.employeetracker.service;

import com.employeetracker.entity.ActivityType;
import com.employeetracker.entity.EmployeeActivity;
import com.employeetracker.repository.EmployeeActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ActivityService {

    private final EmployeeActivityRepository activityRepository;

    public ActivityService(EmployeeActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Transactional
    public EmployeeActivity logActivity(Long userId, ActivityType type, String description,
                                        BigDecimal latitude, BigDecimal longitude, Long referenceId) {
        EmployeeActivity activity = new EmployeeActivity();
        activity.setUserId(userId);
        activity.setActivityType(type);
        activity.setDescription(description);
        activity.setLatitude(latitude);
        activity.setLongitude(longitude);
        activity.setActivityTime(LocalDateTime.now());
        activity.setReferenceId(referenceId);
        return activityRepository.save(activity);
    }
}
