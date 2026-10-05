package com.examly.springapp.exceptions;

// Thrown when a client raises a ticket with a title they have already used.
public class DuplicateTicketException extends RuntimeException {

    public DuplicateTicketException(String message) {
        super(message);
    }
}
