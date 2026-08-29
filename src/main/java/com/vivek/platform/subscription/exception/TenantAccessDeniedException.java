package com.vivek.platform.subscription.exception;

import java.util.UUID;

/**
 * Maps to HTTP 403. Raised when an authenticated principal tries to reach data belonging to an
 * organization it is not a member of.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(UUID organizationId) {
        super("Access denied to organization: " + organizationId);
    }
}
