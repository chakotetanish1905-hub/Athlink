package com.examly.springapp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /api/chat. Send the sessionId from the previous answer to continue a conversation.
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    private String sessionId;

    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
