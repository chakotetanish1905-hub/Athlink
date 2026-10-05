package com.examly.springapp.exceptions;

// Thrown when deleting a support agent fails (for example, the agent still has tickets).
public class AgentDeletionException extends RuntimeException {

    public AgentDeletionException(String message) {
        super(message);
    }
}
