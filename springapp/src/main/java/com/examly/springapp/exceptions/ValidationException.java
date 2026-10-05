package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request breaks a business validation rule (400).
 */
public class ValidationException extends SupportSphereException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
