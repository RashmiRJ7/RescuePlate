package com.foodrescue.algorithm;

/**
 * Pure Java utility for calculating geographic straight-line distance
 * using the Haversine formula on a spherical Earth (R = 6371 km).
 */
public final class DistanceUtil {

    public static final double EARTH_RADIUS_KM = 6371.0;
    public static final int DEFAULT_TRAVEL_MINUTES_PER_KM = 3; // ~20 km/h city traffic

    private DistanceUtil() {
        // Utility class, no instantiation
    }

    /**
     * Calculates the great-circle distance between two points in kilometers,
     * rounded to 1 decimal place.
     */
    public static double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        if (lat1 == lat2 && lng1 == lng2) {
            return 0.0;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2.0) * Math.sin(dLng / 2.0);

        double c = 2.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        double distanceKm = EARTH_RADIUS_KM * c;

        return Math.round(distanceKm * 10.0) / 10.0;
    }

    /**
     * Estimates city travel time in minutes based on distance.
     */
    public static long estimateTravelMinutes(double distanceKm) {
        return estimateTravelMinutes(distanceKm, DEFAULT_TRAVEL_MINUTES_PER_KM);
    }

    public static long estimateTravelMinutes(double distanceKm, int minutesPerKm) {
        return Math.round(distanceKm * minutesPerKm);
    }
}
