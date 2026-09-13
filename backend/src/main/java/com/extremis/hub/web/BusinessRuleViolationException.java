package com.extremis.hub.web;

/**
 * Request is well-formed but violates a business rule (e.g. an
 * unsupported sensitivity-conversion game pair) — mapped to 422 by
 * GlobalExceptionHandler, distinct from a 400 validation error. See
 * docs/06-prd.md §5.2.
 */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
