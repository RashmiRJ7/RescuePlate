package com.foodrescue.dto;

import java.time.Instant;

public record ClaimSummaryResponse(
    Long id,
    String ngoName,
    Instant claimedAt
) {}
