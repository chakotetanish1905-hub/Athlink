package com.examly.springapp.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a ticket cannot be deleted, e.g. it is already assigned or not Open (409).
 */
public class TicketDeletionException extends SupportSphereException {

    public TicketDeletionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
