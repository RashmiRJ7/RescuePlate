package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.entity.Claim;
import com.foodrescue.enums.ClaimStatus;

public record ClaimResponse(
    Long id,
    Long donationId,
    ClaimStatus status,
    Instant claimedAt,
    Instant pickedUpAt,
    Instant deliveredAt,
    Instant cancelledAt,
    String cancelReason,
    ClaimDonationSummaryResponse donation
) {
    /**
     * Backward-compatible 5-argument constructor.
     */
    public ClaimResponse(Long id, Long donationId, ClaimStatus status, Instant claimedAt, ClaimDonationSummaryResponse donation) {
        this(id, donationId, status, claimedAt, null, null, null, null, donation);
    }

    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(
            claim.getId(),
            claim.getDonation().getId(),
            claim.getStatus(),
            claim.getClaimedAt(),
            claim.getPickedUpAt(),
            claim.getDeliveredAt(),
            claim.getCancelledAt(),
            claim.getCancelReason(),
            new ClaimDonationSummaryResponse(
                claim.getDonation().getId(),
                claim.getDonation().getStatus(),
                claim.getDonation().getPickupAddress(),
                claim.getDonation().getSafeUntil()
            )
        );
    }
}
