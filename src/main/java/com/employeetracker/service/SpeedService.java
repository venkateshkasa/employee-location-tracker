package com.employeetracker.service;

import com.employeetracker.entity.EmployeeLocation;
import com.employeetracker.util.HaversineUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

@Service
public class SpeedService {

    public SpeedResult calculate(EmployeeLocation current, Optional<EmployeeLocation> previousLocation) {
        if (previousLocation.isEmpty()) {
            return new SpeedResult(BigDecimal.ZERO, "Idle");
        }

        EmployeeLocation previous = previousLocation.get();
        long seconds = Duration.between(previous.getLocationTime(), current.getLocationTime()).getSeconds();
        if (seconds <= 0) {
            return new SpeedResult(BigDecimal.ZERO, "Idle");
        }

        double meters = HaversineUtil.calculateDistanceMeters(
                previous.getLatitude(),
                previous.getLongitude(),
                current.getLatitude(),
                current.getLongitude()
        );
        double kmph = (meters / 1000.0) / (seconds / 3600.0);
        BigDecimal rounded = BigDecimal.valueOf(kmph).setScale(2, RoundingMode.HALF_UP);
        return new SpeedResult(rounded, classify(kmph));
    }

    private String classify(double kmph) {
        if (kmph < 1.0) {
            return "Idle";
        }
        if (kmph <= 6.0) {
            return "Walking";
        }
        if (kmph <= 40.0) {
            return "Bike";
        }
        return "Car";
    }

    public record SpeedResult(BigDecimal speedKmph, String movementType) {
    }
}
