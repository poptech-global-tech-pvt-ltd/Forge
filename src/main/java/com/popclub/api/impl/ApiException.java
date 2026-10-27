package com.popclub.api.impl;

/**
 * Thrown when an API call fails due to connection issues,
 * timeouts, or unexpected errors at the transport level.
 */
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
