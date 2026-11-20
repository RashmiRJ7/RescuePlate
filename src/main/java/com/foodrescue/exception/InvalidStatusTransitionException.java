package com.foodrescue.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String fromStatus, String toStatus) {
        super("Illegal status transition from " + fromStatus + " to " + toStatus);
    }
}
