package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource conflicts with an existing one, e.g. duplicate user email (409).
 */
public class DuplicateResourceException extends SupportSphereException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
