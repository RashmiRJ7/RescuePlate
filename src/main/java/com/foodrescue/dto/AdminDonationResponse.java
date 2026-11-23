package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.enums.DonationStatus;

public record AdminDonationResponse(
    Long id,
    String foodName,
    String donorName,
    Integer quantity,
    DonationStatus status,
    String pickupAddress,
    String ngoName,
    Instant safeUntil
) {}
