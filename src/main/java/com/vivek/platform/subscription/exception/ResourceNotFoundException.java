package com.vivek.platform.subscription.exception;

/** Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException organization(Object id) {
        return new ResourceNotFoundException("Organization not found: " + id);
    }

    public static ResourceNotFoundException plan(Object type) {
        return new ResourceNotFoundException("Plan not found: " + type);
    }

    public static ResourceNotFoundException invoice(Object number) {
        return new ResourceNotFoundException("Invoice not found: " + number);
    }
}
