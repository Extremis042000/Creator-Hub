package com.extremis.hub.admin;

/** Mapped to 403 by GlobalExceptionHandler -- an authenticated user who isn't an admin. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
