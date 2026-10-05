package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user tries to access a resource they do not own (403).
 */
public class ForbiddenOperationException extends SupportSphereException {

    public ForbiddenOperationException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
