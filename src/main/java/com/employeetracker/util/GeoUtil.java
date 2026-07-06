package com.employeetracker.util;

import java.math.BigDecimal;

public final class GeoUtil {

    private GeoUtil() {
    }

    public static boolean isWithinRadiusMeters(BigDecimal lat1, BigDecimal lon1,
                                               BigDecimal lat2, BigDecimal lon2,
                                               double radiusMeters) {
        double distance = HaversineUtil.calculateDistanceMeters(lat1, lon1, lat2, lon2);
        return distance <= radiusMeters;
    }
}
