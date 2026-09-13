package com.extremis.hub.web;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Standard error shape for every 4xx/5xx — see docs/06-prd.md §4.
 */
@Value
@Builder
public class ApiErrorResponse {
    Instant timestamp;
    int status;
    String error;
    String message;
    String path;
    List<FieldError> fieldErrors;

    @Value
    @Builder
    public static class FieldError {
        String field;
        String message;
    }
}
