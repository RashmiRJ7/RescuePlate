package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.entity.DonationStatusHistory;
import com.foodrescue.enums.DonationStatus;

public record DonationHistoryResponse(
    DonationStatus fromStatus,
    DonationStatus toStatus,
    String changedBy,
    String note,
    Instant changedAt
) {
    public static DonationHistoryResponse from(DonationStatusHistory history) {
        String actor = history.getChangedBy() != null ? history.getChangedBy().getName() : "System";
        return new DonationHistoryResponse(
            history.getFromStatus(),
            history.getToStatus(),
            actor,
            history.getNote(),
            history.getChangedAt()
        );
    }
}
