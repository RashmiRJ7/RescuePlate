package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.entity.Claim;
import com.foodrescue.enums.ClaimStatus;

public record AdminClaimResponse(
    Long id,
    Long donationId,
    String foodName,
    String donorName,
    String ngoName,
    ClaimStatus status,
    Instant claimedAt,
    Instant pickedUpAt,
    Instant deliveredAt,
    Instant cancelledAt,
    String cancelReason
) {
    public static AdminClaimResponse from(Claim claim) {
        return new AdminClaimResponse(
                claim.getId(),
                claim.getDonation().getId(),
                claim.getDonation().getFoodName(),
                claim.getDonation().getDonor().getName(),
                claim.getNgo().getName(),
                claim.getStatus(),
                claim.getClaimedAt(),
                claim.getPickedUpAt(),
                claim.getDeliveredAt(),
                claim.getCancelledAt(),
                claim.getCancelReason());
    }
}
