package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.entity.Claim;
import com.foodrescue.enums.ClaimStatus;

public record ClaimResponse(
    Long id,
    Long donationId,
    ClaimStatus status,
    Instant claimedAt,
    ClaimDonationSummaryResponse donation
) {
    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(
            claim.getId(),
            claim.getDonation().getId(),
            claim.getStatus(),
            claim.getClaimedAt(),
            new ClaimDonationSummaryResponse(
                claim.getDonation().getId(),
                claim.getDonation().getStatus(),
                claim.getDonation().getPickupAddress(),
                claim.getDonation().getSafeUntil()
            )
        );
    }
}
