package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.enums.DonationStatus;

public record ClaimDonationSummaryResponse(
    Long id,
    DonationStatus status,
    String pickupAddress,
    Instant safeUntil
) {}
