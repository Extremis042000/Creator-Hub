package com.extremis.hub.auth;

/** Mapped to 401 by GlobalExceptionHandler. */
public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
        super(message);
    }
}
