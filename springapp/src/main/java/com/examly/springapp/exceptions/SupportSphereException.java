package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Root of the SupportSphere exception hierarchy. Every custom exception extends
 * RuntimeException (via this class) and carries the HTTP status it maps to.
 */
public class SupportSphereException extends RuntimeException {

    private final HttpStatus status;

    public SupportSphereException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SupportSphereException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
