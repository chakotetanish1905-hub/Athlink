package com.examly.springapp.model;

// Answer of POST /api/chat.
public class ChatResponse {

    private String reply;
    private boolean matched;
    private String matchedQuestion;
    private String category;
    private double confidence;
    // "semantic" (Gemini embeddings) or "lexical" (word-overlap fallback)
    private String source;
    private String sessionId;
    private String resolvedQuestion;

    public ChatResponse() {
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getMatchedQuestion() {
        return matchedQuestion;
    }

    public void setMatchedQuestion(String matchedQuestion) {
        this.matchedQuestion = matchedQuestion;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getResolvedQuestion() {
        return resolvedQuestion;
    }

    public void setResolvedQuestion(String resolvedQuestion) {
        this.resolvedQuestion = resolvedQuestion;
    }
}
