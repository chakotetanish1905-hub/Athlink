package com.examly.springapp.exceptions;

// Thrown when a support agent with the same email already exists.
public class DuplicateAgentException extends RuntimeException {

    public DuplicateAgentException(String message) {
        super(message);
    }
}
