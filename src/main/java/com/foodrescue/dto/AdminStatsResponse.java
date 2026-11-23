package com.foodrescue.dto;

import java.time.Instant;

public record AdminStatsResponse(
    long totalUsers,
    long totalDonors,
    long totalNgos,
    long totalDonations,
    long donationsAvailable,
    long donationsDelivered,
    long donationsExpired
) {}
