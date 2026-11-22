package com.foodrescue.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foodrescue.entity.Donation;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DonationResponse(
    Long id,
    String foodName,
    FoodCategory category,
    Integer quantity,
    String description,
    DonationStatus status,
    String pickupAddress,
    Double latitude,
    Double longitude,
    Instant readyFrom,
    Instant safeUntil,
    Long minutesLeft,
    Instant createdAt,
    Instant updatedAt,
    ClaimSummaryResponse claim
) {
    public static DonationResponse from(Donation d, Instant now, ClaimSummaryResponse claim) {
        return new DonationResponse(
            d.getId(),
            d.getFoodName(),
            d.getCategory(),
            d.getQuantity(),
            d.getDescription(),
            d.getStatus(),
            d.getPickupAddress(),
            d.getLatitude(),
            d.getLongitude(),
            d.getReadyFrom(),
            d.getSafeUntil(),
            d.getMinutesLeft(now),
            d.getCreatedAt(),
            d.getUpdatedAt(),
            claim
        );
    }
}
