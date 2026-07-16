package com.employeetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for nearby place search functionality
 */
@Component
@ConfigurationProperties(prefix = "nearby.search")
public class NearbySearchProperties {

    /**
     * Enable or disable nearby place search
     */
    private boolean enabled = true;

    /**
     * Search radius in meters (default 3000m = 3km)
     */
    private int radius = 3000;

    /**
     * Cache distance in meters - if employee moved less than this, reuse cached results
     */
    private int cacheDistance = 200;

    /**
     * API timeout in milliseconds
     */
    private int apiTimeout = 10000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getCacheDistance() {
        return cacheDistance;
    }

    public void setCacheDistance(int cacheDistance) {
        this.cacheDistance = cacheDistance;
    }

    public int getApiTimeout() {
        return apiTimeout;
    }

    public void setApiTimeout(int apiTimeout) {
        this.apiTimeout = apiTimeout;
    }
}
