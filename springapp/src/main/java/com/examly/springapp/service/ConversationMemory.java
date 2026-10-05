package com.examly.springapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Short-term memory of the chatbot: the last 8 turns (question + answer) of every session.
// It lives only in the server's memory; the full transcript is saved in chat_messages.
@Component
public class ConversationMemory {

    public static final int MAX_TURNS = 8;

    // One question and the bot's answer
    public static class Turn {
        private final String userMessage;
        private final String botReply;

        public Turn(String userMessage, String botReply) {
            this.userMessage = userMessage;
            this.botReply = botReply;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public String getBotReply() {
            return botReply;
        }
    }

    // sessionId -> recent turns (ConcurrentHashMap because several users chat at the same time)
    private final Map<String, List<Turn>> sessions = new ConcurrentHashMap<>();

    public synchronized void addTurn(String sessionId, String userMessage, String botReply) {
        List<Turn> turns = sessions.computeIfAbsent(sessionId, key -> new ArrayList<>());
        turns.add(new Turn(userMessage, botReply));
        // Keep only the newest 8 turns
        while (turns.size() > MAX_TURNS) {
            turns.remove(0);
        }
    }

    // Oldest turn first. Returns a copy, so callers cannot change the memory by accident.
    public synchronized List<Turn> getHistory(String sessionId) {
        List<Turn> turns = sessions.get(sessionId);
        if (turns == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(turns);
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
