package com.vivek.platform.subscription.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates domain exceptions into RFC 7807 problem responses. Before this existed, every
 * failure - a missing organization, an unauthorised tenant, a duplicate name - surfaced as an
 * untyped 500 with a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://github.com/vivek/subscription-billing-platform/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "resource-not-found");
    }

    @ExceptionHandler(NoActiveSubscriptionException.class)
    public ProblemDetail handleNoSubscription(NoActiveSubscriptionException ex) {
        return problem(HttpStatus.CONFLICT, "No active subscription", ex.getMessage(), "no-active-subscription");
    }

    @ExceptionHandler(InvalidSubscriptionStateException.class)
    public ProblemDetail handleInvalidState(InvalidSubscriptionStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid subscription state", ex.getMessage(), "invalid-subscription-state");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate resource", ex.getMessage(), "duplicate-resource");
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ProblemDetail handleTenantDenied(TenantAccessDeniedException ex) {
        log.warn("Cross-tenant access rejected: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "tenant-access-denied");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient privileges for this operation", "access-denied");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data (duplicate or constraint violation)",
                "data-integrity-violation");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation-failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), "bad-request");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String slug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
