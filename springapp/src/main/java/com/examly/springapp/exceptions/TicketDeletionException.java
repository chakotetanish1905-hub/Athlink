package com.examly.springapp.exceptions;

// Thrown when a ticket cannot be deleted (for example, an agent is already assigned).
public class TicketDeletionException extends RuntimeException {

    public TicketDeletionException(String message) {
        super(message);
    }
}
