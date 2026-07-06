package com.employeetracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HaversineUtil {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private HaversineUtil() {
    }

    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static double calculateDistanceMeters(BigDecimal lat1, BigDecimal lon1,
                                                 BigDecimal lat2, BigDecimal lon2) {
        return calculateDistanceMeters(
                lat1.doubleValue(), lon1.doubleValue(),
                lat2.doubleValue(), lon2.doubleValue()
        );
    }

    public static double metersToKilometers(double meters) {
        return BigDecimal.valueOf(meters / 1000.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
