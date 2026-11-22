package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;

public record DonationFeedResponse(
    Long id,
    String foodName,
    FoodCategory category,
    Integer quantity,
    String donorName,
    String pickupAddress,
    Double distanceKm,
    Long minutesLeft,
    Instant safeUntil,
    Double urgencyScore,
    boolean exceedsCapacity,
    DonationStatus status
) {}
