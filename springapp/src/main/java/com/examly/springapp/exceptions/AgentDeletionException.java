package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a support agent cannot be deleted because of dependencies such as assigned tickets (409).
 */
public class AgentDeletionException extends SupportSphereException {

    public AgentDeletionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
