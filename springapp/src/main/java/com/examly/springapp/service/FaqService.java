package com.examly.springapp.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.Faq;
import com.examly.springapp.model.FaqEntity;
import com.examly.springapp.repository.FaqRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

// The chatbot's FAQ knowledge base.
// On startup faqs.json is seeded into the faqs table (once, with Gemini embeddings when enabled)
// and then loaded into memory. findBestMatch() finds the FAQ closest to a question.
@Service
public class FaqService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FaqService.class);

    // Common words that say nothing about the topic of a question
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "for", "how", "can", "what", "why", "does", "do", "is", "are",
            "my", "to", "of", "in", "on", "me", "it", "this", "that", "with", "about", "where", "when",
            "you", "your", "our", "we", "be", "get", "i", "im", "there", "any", "way", "please", "help",
            "who", "which", "will", "am", "if", "so", "at", "by", "from", "as", "not", "dont", "cant"));

    private final FaqRepository faqRepository;
    private final GeminiService geminiService;
    private final double similarityThreshold;
    private final double lexicalThreshold;

    // FAQs kept in memory after startup
    private List<FaqEntity> faqs = new ArrayList<>();

    public FaqService(FaqRepository faqRepository, GeminiService geminiService,
            @Value("${chatbot.similarity.threshold:0.65}") double similarityThreshold,
            @Value("${chatbot.lexical.threshold:0.08}") double lexicalThreshold) {
        this.faqRepository = faqRepository;
        this.geminiService = geminiService;
        this.similarityThreshold = similarityThreshold;
        this.lexicalThreshold = lexicalThreshold;
    }

    // Result of a search: the closest FAQ (null when nothing passed the threshold) and its score
    public static class FaqMatch {
        private final FaqEntity faq;
        private final double score;
        private final boolean semantic;

        public FaqMatch(FaqEntity faq, double score, boolean semantic) {
            this.faq = faq;
            this.score = score;
            this.semantic = semantic;
        }

        public FaqEntity getFaq() {
            return faq;
        }

        public double getScore() {
            return score;
        }

        public boolean isSemantic() {
            return semantic;
        }

        public boolean isMatched() {
            return faq != null;
        }
    }

    @PostConstruct
    public void init() {
        seedFaqs();
        faqs = faqRepository.findAll();
        LOGGER.info("Loaded {} FAQs (Gemini {})", faqs.size(), geminiService.isEnabled() ? "enabled" : "disabled");
    }

    public List<FaqEntity> getAllFaqs() {
        return faqs;
    }

    // Gemini enabled (queryEmbedding not null): cosine similarity against every FAQ embedding.
    // Gemini disabled (queryEmbedding null): word overlap (Jaccard) against every FAQ question.
    public FaqMatch findBestMatch(String query, float[] queryEmbedding) {
        if (queryEmbedding != null) {
            FaqEntity best = null;
            double bestScore = 0;
            for (FaqEntity faq : faqs) {
                if (faq.getEmbedding() == null) {
                    continue;
                }
                double score = cosineSimilarity(queryEmbedding, faq.getEmbedding());
                if (score > bestScore) {
                    bestScore = score;
                    best = faq;
                }
            }
            // Only answer from an FAQ that is similar enough
            if (best != null && bestScore >= similarityThreshold) {
                return new FaqMatch(best, bestScore, true);
            }
            if (best != null) {
                return new FaqMatch(null, bestScore, true);
            }
            // No FAQ has an embedding yet: use the word-overlap search instead
        }

        Set<String> queryWords = tokenize(query);
        FaqEntity best = null;
        double bestScore = 0;
        for (FaqEntity faq : faqs) {
            double score = jaccardSimilarity(queryWords, tokenize(faq.getQuestion()));
            if (score > bestScore) {
                bestScore = score;
                best = faq;
            }
        }
        if (best != null && bestScore >= lexicalThreshold) {
            return new FaqMatch(best, bestScore, false);
        }
        return new FaqMatch(null, bestScore, false);
    }

    // cos(a, b) = (a . b) / (|a| * |b|), between -1 and 1 (1 = same meaning)
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Jaccard = shared words / all different words
    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) shared.size() / all.size();
    }

    // "Editing my tickets?" -> {"edit", "ticket"}: lower case, no punctuation, no stop words, simple stemming
    public static Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }
        String[] parts = text.toLowerCase().replace("'", "").replaceAll("[^a-z0-9]+", " ").trim().split("\\s+");
        for (String word : parts) {
            if (word.length() < 2 || STOP_WORDS.contains(word)) {
                continue;
            }
            words.add(stem(word));
        }
        return words;
    }

    // Very small stemmer so that "tickets", "editing" and "resolved" match "ticket", "edit" and "resolve"
    private static String stem(String word) {
        if (word.length() > 5 && word.endsWith("ing")) {
            return word.substring(0, word.length() - 3);
        }
        if (word.length() > 4 && word.endsWith("ed")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("es")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && (word.endsWith("s") || word.endsWith("e"))) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    // Saves every FAQ from faqs.json that is not in the database yet.
    // When Gemini is enabled, FAQs without an embedding get one (so adding a key later also works).
    private void seedFaqs() {
        List<Faq> seedFaqs;
        try (InputStream input = new ClassPathResource("faqs.json").getInputStream()) {
            seedFaqs = new ObjectMapper().readValue(input, new TypeReference<List<Faq>>() { });
        } catch (Exception e) {
            LOGGER.error("Could not read faqs.json", e);
            return;
        }

        for (Faq faq : seedFaqs) {
            // Absent is a normal case here: it means the FAQ still has to be seeded
            Optional<FaqEntity> existing = faqRepository.findById(faq.getId());
            boolean isNew = existing.isEmpty();
            FaqEntity entity = existing.orElseGet(
                    () -> new FaqEntity(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer()));

            boolean needsEmbedding = geminiService.isEnabled() && entity.getEmbedding() == null;
            if (needsEmbedding) {
                entity.setEmbedding(geminiService.embed(faq.getQuestion() + "\n" + faq.getAnswer()));
            }

            if (isNew || (needsEmbedding && entity.getEmbedding() != null)) {
                faqRepository.save(entity);
            }
        }
    }
}
