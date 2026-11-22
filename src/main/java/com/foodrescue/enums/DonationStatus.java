package com.foodrescue.enums;

public enum DonationStatus {
    AVAILABLE,
    DISPATCHING,
    RESERVED,
    PICKED_UP,
    DELIVERED,
    EXPIRED,
    CANCELLED;

    public boolean isClaimable() {
        return this == AVAILABLE || this == DISPATCHING;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == EXPIRED || this == CANCELLED;
    }
}
