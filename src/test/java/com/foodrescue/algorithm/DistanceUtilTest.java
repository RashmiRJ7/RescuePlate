package com.foodrescue.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistanceUtilTest {

    @Test
    void sameLocationReturnsZero() {
        double distance = DistanceUtil.calculateDistanceKm(15.8497, 74.4977, 15.8497, 74.4977);
        assertEquals(0.0, distance, 0.01);
    }

    @Test
    void knownCityPair_LondonToParis_WithinOneKmOfRealDistance() {
        // London: 51.5074 N, 0.1278 W (-0.1278)
        // Paris: 48.8566 N, 2.3522 E
        // Known spherical great-circle distance is ~343.5 - 344.0 km
        double distance = DistanceUtil.calculateDistanceKm(51.5074, -0.1278, 48.8566, 2.3522);
        assertTrue(distance >= 342.5 && distance <= 345.5, "Expected ~344 km, got " + distance);
    }

    @Test
    void knownCityPair_BelagaviToDharwad_WithinExpectedRange() {
        // Belagavi: 15.8497, 74.4977
        // Dharwad: 15.4589, 75.0078
        // Straight line distance is ~69.6 km
        double distance = DistanceUtil.calculateDistanceKm(15.8497, 74.4977, 15.4589, 75.0078);
        assertTrue(distance >= 68.0 && distance <= 71.0, "Expected ~69.6 km, got " + distance);
    }

    @Test
    void estimateTravelMinutes_TenKm_ReturnsThirtyMinutes() {
        long minutes = DistanceUtil.estimateTravelMinutes(10.0);
        assertEquals(30, minutes); // 10 km * 3 min/km = 30 min
    }
}
