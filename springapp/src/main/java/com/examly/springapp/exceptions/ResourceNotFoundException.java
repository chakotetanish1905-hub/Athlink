package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist (404).
 */
public class ResourceNotFoundException extends SupportSphereException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
