package com.examly.springapp.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

// One question + answer of a chatbot conversation (table: chat_messages).
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    @Column(length = 1000)
    private String userMessage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String botReply;

    // Id of the matched FAQ, or null when no FAQ was close enough
    private Long matchedFaqId;

    private Double similarityScore;

    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public ChatMessage(String sessionId, String userMessage, String botReply, Long matchedFaqId,
            Double similarityScore) {
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.botReply = botReply;
        this.matchedFaqId = matchedFaqId;
        this.similarityScore = similarityScore;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getBotReply() {
        return botReply;
    }

    public void setBotReply(String botReply) {
        this.botReply = botReply;
    }

    public Long getMatchedFaqId() {
        return matchedFaqId;
    }

    public void setMatchedFaqId(Long matchedFaqId) {
        this.matchedFaqId = matchedFaqId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
