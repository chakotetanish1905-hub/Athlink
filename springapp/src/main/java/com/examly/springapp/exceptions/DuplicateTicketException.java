package com.examly.springapp.exceptions;

/**
 * Thrown when attempting to create a ticket that duplicates an existing ticket title for the same client (409).
 */
public class DuplicateTicketException extends DuplicateResourceException {

    public DuplicateTicketException(String message) {
        super(message);
    }
}
