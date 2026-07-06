package com.employeetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrackingProperties {

    @Value("${tracking.stop.radius-meters:30}")
    private double stopRadiusMeters;

    @Value("${tracking.stop.duration-minutes:10}")
    private int stopDurationMinutes;

    @Value("${tracking.online.threshold-minutes:15}")
    private int onlineThresholdMinutes;

    @Value("${tracking.auto-update.interval-minutes:10}")
    private int autoUpdateIntervalMinutes;

    public double getStopRadiusMeters() {
        return stopRadiusMeters;
    }

    public int getStopDurationMinutes() {
        return stopDurationMinutes;
    }

    public int getOnlineThresholdMinutes() {
        return onlineThresholdMinutes;
    }

    public int getAutoUpdateIntervalMinutes() {
        return autoUpdateIntervalMinutes;
    }
}
