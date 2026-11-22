package com.foodrescue.exception;

public class DonationNotClaimableException extends RuntimeException {

    public DonationNotClaimableException(String message) {
        super(message);
    }

    public DonationNotClaimableException() {
        super("Donation is no longer available.");
    }
}
