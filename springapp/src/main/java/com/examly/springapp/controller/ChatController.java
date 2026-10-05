package com.examly.springapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.model.ChatMessage;
import com.examly.springapp.model.ChatRequest;
import com.examly.springapp.model.ChatResponse;
import com.examly.springapp.model.FaqEntity;
import com.examly.springapp.service.ChatService;
import com.examly.springapp.service.FaqService;

import jakarta.validation.Valid;

// FAQ chatbot endpoints (Phase 2). All of them are public - see SecurityConfig.
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final FaqService faqService;

    public ChatController(ChatService chatService, FaqService faqService) {
        this.chatService = chatService;
        this.faqService = faqService;
    }

    // 200 with the bot's reply. Send sessionId to continue a conversation.
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    // 200 with the saved transcript of a session, oldest message first
    @GetMapping("/chat/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }

    // 204 - clears the short-term conversation memory of the session
    @DeleteMapping("/chat/memory/{sessionId}")
    public ResponseEntity<Void> clearMemory(@PathVariable String sessionId) {
        chatService.clearMemory(sessionId);
        return ResponseEntity.noContent().build();
    }

    // 200 with every FAQ (the embedding vectors are not included)
    @GetMapping("/faqs")
    public ResponseEntity<List<FaqEntity>> getFaqs() {
        return ResponseEntity.ok(faqService.getAllFaqs());
    }
}
