package com.extremis.hub.admin;

/** Mapped to 401 by GlobalExceptionHandler -- no valid session at all. */
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException(String message) {
        super(message);
    }
}
