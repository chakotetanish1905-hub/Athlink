package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // GET /api/chat/history/{sessionId} - oldest message first
    List<ChatMessage> findBySessionIdOrderByIdAsc(String sessionId);
}
