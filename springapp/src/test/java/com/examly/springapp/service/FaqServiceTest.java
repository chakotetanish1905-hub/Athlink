package com.examly.springapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.examly.springapp.model.FaqEntity;
import com.examly.springapp.repository.FaqRepository;

class FaqServiceTest {

    private FaqEntity faq(long id, String question, float[] embedding) {
        FaqEntity faq = new FaqEntity(id, "Tickets", question, "Answer " + id);
        faq.setEmbedding(embedding);
        return faq;
    }

    // FaqService with the given FAQs "in the database" and Gemini disabled
    private FaqService serviceWith(List<FaqEntity> faqs) {
        FaqRepository repo = mock(FaqRepository.class);
        when(repo.findById(anyLong())).thenReturn(Optional.of(new FaqEntity()));   // already seeded
        when(repo.findAll()).thenReturn(faqs);
        FaqService service = new FaqService(repo, new GeminiService("", "m1", "m2"), 0.65, 0.08);
        service.init();
        return service;
    }

    @Test
    void cosineSimilarityOfVectors() {
        assertEquals(1.0, FaqService.cosineSimilarity(new float[] { 1, 2, 3 }, new float[] { 2, 4, 6 }), 0.0001);
        assertEquals(0.0, FaqService.cosineSimilarity(new float[] { 1, 0 }, new float[] { 0, 1 }), 0.0001);
        assertEquals(0.0, FaqService.cosineSimilarity(new float[] { 1, 0 }, new float[] { 1, 0, 0 }), 0.0001);
    }

    @Test
    void semanticSearchPicksTheClosestFaqAboveTheThreshold() {
        FaqService service = serviceWith(List.of(
                faq(1, "Create a ticket", new float[] { 1, 0, 0 }),
                faq(2, "Delete a ticket", new float[] { 0, 1, 0 })));

        FaqService.FaqMatch match = service.findBestMatch("anything", new float[] { 0.1f, 0.9f, 0 });
        assertTrue(match.isMatched());
        assertTrue(match.isSemantic());
        assertEquals(2L, match.getFaq().getId());
    }

    @Test
    void semanticSearchBelowTheThresholdIsNoMatch() {
        FaqService service = serviceWith(List.of(faq(1, "Create a ticket", new float[] { 1, 0, 0 })));

        FaqService.FaqMatch match = service.findBestMatch("weather", new float[] { 0, 0, 1 });
        assertFalse(match.isMatched());
        assertTrue(match.getScore() < 0.65);
    }

    @Test
    void lexicalFallbackUsesWordOverlap() {
        FaqService service = serviceWith(List.of(
                faq(1, "How do I create or raise a new ticket?", null),
                faq(2, "How do I edit or delete my ticket?", null)));

        FaqService.FaqMatch match = service.findBestMatch("Can I delete my tickets?", null);
        assertTrue(match.isMatched());
        assertFalse(match.isSemantic());
        assertEquals(2L, match.getFaq().getId());
    }

    @Test
    void lexicalFallbackBelowTheThresholdIsNoMatch() {
        FaqService service = serviceWith(List.of(faq(1, "How do I create or raise a new ticket?", null)));

        FaqService.FaqMatch match = service.findBestMatch("What is the weather in Paris?", null);
        assertFalse(match.isMatched());
    }

    @Test
    void tokenizeRemovesStopWordsAndStems() {
        assertEquals(java.util.Set.of("edit", "ticket"), FaqService.tokenize("How do I edit my tickets?"));
        assertEquals(java.util.Set.of("edit", "delet", "ticket"), FaqService.tokenize("Why can't I edit or delete my ticket?"));
    }

    @Test
    void seedingSavesNewFaqsFromJsonWithoutGemini() {
        FaqRepository repo = mock(FaqRepository.class);
        when(repo.findById(anyLong())).thenReturn(Optional.empty());
        FaqService service = new FaqService(repo, new GeminiService("", "m1", "m2"), 0.65, 0.08);
        service.init();
        verify(repo, org.mockito.Mockito.times(15)).save(any(FaqEntity.class));
    }

    @Test
    void seedingSkipsFaqsThatAlreadyExist() {
        FaqRepository repo = mock(FaqRepository.class);
        when(repo.findById(anyLong())).thenReturn(Optional.of(new FaqEntity()));
        new FaqService(repo, new GeminiService("", "m1", "m2"), 0.65, 0.08).init();
        verify(repo, never()).save(any(FaqEntity.class));
    }
}
