package com.examly.springapp.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeminiServiceTest {

    @Test
    void enabledOnlyWhenAnApiKeyIsConfigured() {
        assertTrue(new GeminiService("test-key", "gemini-embedding-2", "gemini-2.5-flash").isEnabled());
        assertFalse(new GeminiService("", "gemini-embedding-2", "gemini-2.5-flash").isEnabled());
        assertFalse(new GeminiService("   ", "gemini-embedding-2", "gemini-2.5-flash").isEnabled());
        assertFalse(new GeminiService(null, "gemini-embedding-2", "gemini-2.5-flash").isEnabled());
    }

    @Test
    void disabledServiceNeverCallsTheApi() {
        GeminiService gemini = new GeminiService("", "gemini-embedding-2", "gemini-2.5-flash");
        assertNull(gemini.embed("How do I raise a ticket?"));
        assertNull(gemini.generate("Hello"));
    }
}
