package com.foodrescue.enums;

public enum ClaimStatus {
    ACTIVE,
    PICKED_UP,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public boolean isOpen() {
        return this == ACTIVE || this == PICKED_UP;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }
}
