package com.examly.springapp.exceptions;

/**
 * Thrown when attempting to create a support agent with duplicate email/phone (409).
 */
public class DuplicateAgentException extends DuplicateResourceException {

    public DuplicateAgentException(String message) {
        super(message);
    }
}
