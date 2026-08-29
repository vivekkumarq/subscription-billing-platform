package com.vivek.platform.subscription.exception;

import java.util.UUID;

/** Maps to HTTP 409 - the organization exists but has nothing to bill against. */
public class NoActiveSubscriptionException extends RuntimeException {

    public NoActiveSubscriptionException(UUID organizationId) {
        super("No active subscription for organization: " + organizationId);
    }
}
