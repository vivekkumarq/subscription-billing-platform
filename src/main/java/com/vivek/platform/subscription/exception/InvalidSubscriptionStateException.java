package com.vivek.platform.subscription.exception;

/** Maps to HTTP 409 - the requested lifecycle transition is not legal from the current state. */
public class InvalidSubscriptionStateException extends RuntimeException {

    public InvalidSubscriptionStateException(String message) {
        super(message);
    }
}
