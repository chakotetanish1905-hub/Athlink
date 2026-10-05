package com.examly.springapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.examly.springapp.model.ChatMessage;
import com.examly.springapp.model.ChatRequest;
import com.examly.springapp.model.ChatResponse;
import com.examly.springapp.model.FaqEntity;
import com.examly.springapp.repository.ChatMessageRepository;
import com.examly.springapp.repository.FaqRepository;

class ChatServiceTest {

    private GeminiService gemini;
    private ChatMessageRepository chatRepo;
    private ConversationMemory memory;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        FaqEntity create = new FaqEntity(1L, "Tickets", "How do I create or raise a new ticket?", "Use Add Ticket.");
        create.setEmbedding(new float[] { 1, 0, 0 });
        FaqEntity edit = new FaqEntity(2L, "Tickets", "How do I edit or delete my ticket?", "Edit while Open.");
        edit.setEmbedding(new float[] { 0, 1, 0 });

        FaqRepository faqRepo = mock(FaqRepository.class);
        when(faqRepo.findById(anyLong())).thenReturn(Optional.of(new FaqEntity()));
        when(faqRepo.findAll()).thenReturn(List.of(create, edit));

        gemini = mock(GeminiService.class);   // disabled by default (isEnabled() -> false)
        FaqService faqService = new FaqService(faqRepo, gemini, 0.65, 0.08);
        faqService.init();

        chatRepo = mock(ChatMessageRepository.class);
        memory = new ConversationMemory();
        chatService = new ChatService(gemini, faqService, memory, chatRepo);
    }

    @Test
    void lexicalAnswerWithoutGeminiIsTheFaqAnswer() {
        ChatResponse response = chatService.chat(new ChatRequest("How do I create a ticket?", null));

        assertTrue(response.isMatched());
        assertEquals("Use Add Ticket.", response.getReply());
        assertEquals("How do I create or raise a new ticket?", response.getMatchedQuestion());
        assertEquals("Tickets", response.getCategory());
        assertEquals("lexical", response.getSource());
        assertNotNull(response.getSessionId());
        assertTrue(response.getConfidence() > 0);
    }

    @Test
    void followUpQuestionUsesTheSessionMemory() {
        ChatResponse first = chatService.chat(new ChatRequest("How do I create a ticket?", null));
        ChatResponse second = chatService.chat(new ChatRequest("What about editing it?", first.getSessionId()));

        assertEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getResolvedQuestion().contains("ticket"));
        assertEquals(2L, matchedId(second));
        assertEquals(2, memory.getHistory(first.getSessionId()).size());
    }

    @Test
    void newUnrelatedQuestionDoesNotBorrowThePreviousTopic() {
        ChatResponse first = chatService.chat(new ChatRequest("How do I create a ticket?", null));
        ChatResponse second = chatService.chat(new ChatRequest("What is the weather in Paris?", first.getSessionId()));

        assertEquals("What is the weather in Paris?", second.getResolvedQuestion());
        assertFalse(second.isMatched());
    }

    @Test
    void unrelatedQuestionIsNoMatch() {
        ChatResponse response = chatService.chat(new ChatRequest("What is the weather in Paris?", "s1"));

        assertFalse(response.isMatched());
        assertEquals(ChatService.NO_MATCH_REPLY, response.getReply());
        assertEquals(null, response.getMatchedQuestion());
    }

    @Test
    void everyInteractionIsSaved() {
        chatService.chat(new ChatRequest("How do I create a ticket?", "s1"));

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepo).save(saved.capture());
        assertEquals("s1", saved.getValue().getSessionId());
        assertEquals("How do I create a ticket?", saved.getValue().getUserMessage());
        assertEquals(1L, saved.getValue().getMatchedFaqId());
        assertNotNull(saved.getValue().getCreatedAt());
    }

    @Test
    void semanticMatchWithGeminiUsesAGroundedGeneratedReply() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.embed(anyString())).thenReturn(new float[] { 0, 1, 0 });
        when(gemini.generate(anyString())).thenReturn("You can edit it while it is Open.");

        ChatResponse response = chatService.chat(new ChatRequest("Can I change my ticket?", null));

        assertEquals("semantic", response.getSource());
        assertEquals("How do I edit or delete my ticket?", response.getMatchedQuestion());
        assertEquals("You can edit it while it is Open.", response.getReply());
        assertEquals(1.0, response.getConfidence(), 0.001);
    }

    @Test
    void geminiFailureFallsBackToLexicalSearchAndTheFaqAnswer() {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.embed(anyString())).thenReturn(null);      // embedding call failed
        when(gemini.generate(anyString())).thenReturn(null);   // generation call failed

        ChatResponse response = chatService.chat(new ChatRequest("How do I delete my ticket?", null));

        assertEquals("lexical", response.getSource());
        assertEquals("Edit while Open.", response.getReply());
    }

    @Test
    void clearMemoryKeepsTheSavedTranscript() {
        chatService.chat(new ChatRequest("How do I create a ticket?", "s1"));
        chatService.clearMemory("s1");

        assertTrue(memory.getHistory("s1").isEmpty());
        verify(chatRepo, org.mockito.Mockito.never()).deleteAll();
        verify(chatRepo, org.mockito.Mockito.times(1)).save(any(ChatMessage.class));
    }

    private Long matchedId(ChatResponse response) {
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepo, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        List<ChatMessage> all = saved.getAllValues();
        return all.get(all.size() - 1).getMatchedFaqId();
    }
}
