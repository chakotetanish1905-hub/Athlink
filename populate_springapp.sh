#!/usr/bin/env bash
# =====================================================================================
#  SupportSphere - populate the Spring Boot backend (service + service/impl and
#  everything they need: models, repositories, exceptions, JWT config, controllers,
#  pom.xml, application.properties, faqs.json and tests). Includes Phase 2 (AI FAQ chatbot).
#
#  Usage (run from the project folder that contains "springapp", or pass its path):
#      bash populate_springapp.sh                 # uses ./springapp
#      bash populate_springapp.sh /path/to/project
#      bash populate_springapp.sh /path/to/project/springapp
#
#  - Folders are created only if they don't exist (mkdir -p); existing files are overwritten.
#  - Old files from the previous version (extra DTOs, CurrentUserService, ErrorLogService,
#    service implementations outside service/impl, SwaggerConfig, ...) are deleted,
#    otherwise the project would not compile.
#  - After running:  cd springapp && mvn clean test
# =====================================================================================
set -e

TARGET="${1:-.}"
if [ -d "$TARGET/springapp" ]; then
  APP="$TARGET/springapp"
elif [ "$(basename "$(cd "$TARGET" && pwd)")" = "springapp" ] || [ -f "$TARGET/pom.xml" ]; then
  APP="$TARGET"
else
  APP="$TARGET/springapp"
  echo "No springapp folder found - creating $APP"
fi
APP="$(mkdir -p "$APP" && cd "$APP" && pwd)"
echo "Populating backend in: $APP"

JAVA="$APP/src/main/java/com/examly/springapp"
TEST="$APP/src/test/java/com/examly/springapp"

# ---------- create the folder structure (no-op if it already exists) ----------
mkdir -p "$JAVA/config" "$JAVA/controller" "$JAVA/exceptions" "$JAVA/model" "$JAVA/repository" \
         "$JAVA/service/impl" "$APP/src/main/resources" \
         "$TEST/config" "$TEST/controller" "$TEST/service/impl" "$APP/src/test/resources"

# ---------- remove files of the previous version that no longer exist ----------
rm -f "$APP/src/main/java/com/examly/springapp/config/SwaggerConfig.java"
rm -f "$APP/src/main/java/com/examly/springapp/exceptions/DuplicateResourceException.java"
rm -f "$APP/src/main/java/com/examly/springapp/exceptions/ForbiddenOperationException.java"
rm -f "$APP/src/main/java/com/examly/springapp/exceptions/ResourceNotFoundException.java"
rm -f "$APP/src/main/java/com/examly/springapp/exceptions/SupportSphereException.java"
rm -f "$APP/src/main/java/com/examly/springapp/exceptions/ValidationException.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/EntityRefDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/ErrorResponseDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/FeedbackRequestDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/FeedbackResponseDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/LoginRequestDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/SupportAgentRequestDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/SupportAgentResponseDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/TicketRequestDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/TicketResponseDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/UserRequestDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/model/UserResponseDTO.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/CurrentUserService.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/CurrentUserServiceImpl.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/ErrorLogService.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/ErrorLogServiceImpl.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/FeedbackServiceImpl.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/SupportAgentServiceImpl.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/TicketServiceImpl.java"
rm -f "$APP/src/main/java/com/examly/springapp/service/UserServiceImpl.java"
rm -f "$APP/src/test/java/com/examly/springapp/service/TicketServiceImplTest.java"
echo "Removed old files from the previous version (if they existed)"

# =====================================================================================
# 1. SERVICE INTERFACES + PHASE 2 SERVICES  (service/)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/ChatService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.examly.springapp.model.ChatMessage;
import com.examly.springapp.model.ChatRequest;
import com.examly.springapp.model.ChatResponse;
import com.examly.springapp.model.FaqEntity;
import com.examly.springapp.repository.ChatMessageRepository;

// The FAQ chatbot. It only answers questions from the FAQ knowledge base - it never performs actions.
//
//   user message
//     -> recent turns from ConversationMemory
//     -> follow-up turned into a standalone question ("What about editing it?" -> about tickets)
//     -> FaqService.findBestMatch (Gemini embeddings, or word overlap when Gemini is disabled)
//     -> reply grounded in the matched FAQ (written by Gemini when enabled, otherwise the FAQ answer)
//     -> saved in ConversationMemory and in the chat_messages table
@Service
public class ChatService {

    public static final String NO_MATCH_REPLY = "I couldn't find that in the SupportSphere FAQs. "
            + "I can help with tickets, support agents, feedback and account questions - "
            + "or raise a ticket and the support team will follow up.";

    // Topic words used to understand short follow-up questions without Gemini
    private static final Set<String> TOPIC_WORDS = FaqService.tokenize(
            "ticket agent feedback review account password summary login logout priority category rating");

    // Words and openings that refer back to the previous question ("What about editing it?")
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "^(what about|how about|and|also)\\b.*|.*\\b(it|its|that|this|them|those|these|one)\\b.*",
            Pattern.CASE_INSENSITIVE);

    private final GeminiService geminiService;
    private final FaqService faqService;
    private final ConversationMemory conversationMemory;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(GeminiService geminiService, FaqService faqService, ConversationMemory conversationMemory,
            ChatMessageRepository chatMessageRepository) {
        this.geminiService = geminiService;
        this.faqService = faqService;
        this.conversationMemory = conversationMemory;
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatResponse chat(ChatRequest request) {
        // A new conversation gets a new session id; Angular sends it back with the next message
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String message = request.getMessage().trim();
        List<ConversationMemory.Turn> history = conversationMemory.getHistory(sessionId);

        // 1. Understand follow-up questions using the recent conversation
        String resolvedQuestion = resolveQuestion(message, history);

        // 2. Find the closest FAQ (embedding is null when Gemini is disabled or the call failed)
        float[] queryEmbedding = null;
        if (geminiService.isEnabled()) {
            queryEmbedding = geminiService.embed(resolvedQuestion);
        }
        FaqService.FaqMatch match = faqService.findBestMatch(resolvedQuestion, queryEmbedding);

        // 3. Build the reply - only from the matched FAQ, never from Gemini's own knowledge
        String reply;
        if (match.isMatched()) {
            reply = null;
            if (geminiService.isEnabled()) {
                reply = geminiService.generate(groundedPrompt(match.getFaq(), resolvedQuestion, history));
            }
            if (reply == null) {
                reply = match.getFaq().getAnswer();
            }
        } else {
            reply = NO_MATCH_REPLY;
        }

        // 4. Remember the turn and save it
        conversationMemory.addTurn(sessionId, resolvedQuestion, reply);
        Long matchedFaqId = match.isMatched() ? match.getFaq().getId() : null;
        chatMessageRepository.save(new ChatMessage(sessionId, message, reply, matchedFaqId, round(match.getScore())));

        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        response.setMatched(match.isMatched());
        response.setMatchedQuestion(match.isMatched() ? match.getFaq().getQuestion() : null);
        response.setCategory(match.isMatched() ? match.getFaq().getCategory() : null);
        response.setConfidence(round(match.getScore()));
        response.setSource(match.isSemantic() ? "semantic" : "lexical");
        response.setSessionId(sessionId);
        response.setResolvedQuestion(resolvedQuestion);
        return response;
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
    }

    // Clears only the short-term memory; the saved transcript stays in chat_messages
    public void clearMemory(String sessionId) {
        conversationMemory.clear(sessionId);
    }

    // Turns a follow-up like "What about editing it?" into a question that makes sense on its own
    private String resolveQuestion(String message, List<ConversationMemory.Turn> history) {
        if (history.isEmpty()) {
            return message;
        }

        if (geminiService.isEnabled()) {
            String prompt = "Rewrite the user's last message as one standalone question about the SupportSphere "
                    + "support application, using the conversation for context. If it is already standalone, "
                    + "return it unchanged. Reply with the question only.\n\n"
                    + "Conversation:\n" + formatHistory(history)
                    + "\nUser's last message: " + message;
            String rewritten = geminiService.generate(prompt);
            if (rewritten != null && !rewritten.isBlank()) {
                return rewritten.replace("\n", " ").trim();
            }
        }

        // Without Gemini: a message that refers back ("it", "that", "what about ...") and names no topic
        // borrows the topic of the previous question. Other messages are treated as new questions.
        if (!FOLLOW_UP.matcher(message.trim()).matches()) {
            return message;
        }
        Set<String> messageWords = FaqService.tokenize(message);
        for (String word : messageWords) {
            if (TOPIC_WORDS.contains(word)) {
                return message;
            }
        }
        String previousQuestion = history.get(history.size() - 1).getUserMessage();
        List<String> previousTopics = new ArrayList<>();
        for (String word : FaqService.tokenize(previousQuestion)) {
            if (TOPIC_WORDS.contains(word)) {
                previousTopics.add(word);
            }
        }
        if (previousTopics.isEmpty()) {
            return message;
        }
        return message + " (about " + String.join(", ", previousTopics) + ")";
    }

    // The prompt tells Gemini to answer only with the information in the matched FAQ
    private String groundedPrompt(FaqEntity faq, String question, List<ConversationMemory.Turn> history) {
        return "You are SupportSphere AI, the assistant of the SupportSphere support-ticket application.\n"
                + "Answer the user's question using ONLY the FAQ below. Do not add features or steps that the FAQ "
                + "does not mention. Do not offer to perform actions yourself. Keep the answer short and friendly "
                + "(at most 3 sentences).\n\n"
                + "FAQ question: " + faq.getQuestion() + "\n"
                + "FAQ answer: " + faq.getAnswer() + "\n\n"
                + "Recent conversation:\n" + formatHistory(history) + "\n"
                + "User question: " + question;
    }

    private String formatHistory(List<ConversationMemory.Turn> history) {
        StringBuilder text = new StringBuilder();
        for (ConversationMemory.Turn turn : history) {
            text.append("User: ").append(turn.getUserMessage()).append("\n");
            text.append("Assistant: ").append(turn.getBotReply()).append("\n");
        }
        return text.toString();
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/ChatService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/ConversationMemory.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/ConversationMemory.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/FaqService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
            FaqEntity entity = faqRepository.findById(faq.getId()).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new FaqEntity(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer());
            }

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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/FaqService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/FeedbackService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.util.List;

import com.examly.springapp.model.Feedback;

public interface FeedbackService {

    Feedback createFeedback(Feedback feedback);

    Feedback getFeedbackById(Long feedbackId);

    List<Feedback> getAllFeedbacks();

    Feedback deleteFeedback(Long feedbackId);

    List<Feedback> getFeedbacksByUserId(Long userId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/FeedbackService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/GeminiService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Calls the Google Gemini REST API with Java's built-in HttpClient and Jackson (no extra libraries).
//   embed(text)      -> semantic vector of the text (embedding model)
//   generate(prompt) -> text written by Gemini (generation model)
//   isEnabled()      -> false when no API key is configured; the chatbot then uses lexical matching
// Both API methods return null when Gemini is disabled or the call fails, so callers can fall back.
@Service
public class GeminiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiService.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String embeddingModel;
    private final String generationModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiService(@Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.embedding.model}") String embeddingModel,
            @Value("${gemini.generation.model}") String generationModel) {
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.generationModel = generationModel;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public float[] embed(String text) {
        if (!isEnabled()) {
            return null;
        }
        try {
            // { "model": "models/<model>", "content": { "parts": [ { "text": "..." } ] } }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "models/" + embeddingModel);
            body.set("content", textContent(text));

            JsonNode response = post(embeddingModel + ":embedContent", body);
            JsonNode values = response.path("embedding").path("values");
            if (!values.isArray() || values.size() == 0) {
                LOGGER.warn("Gemini embedding response had no values");
                return null;
            }

            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            LOGGER.warn("Gemini embedding failed: {}", e.getMessage());
            return null;
        }
    }

    public String generate(String prompt) {
        if (!isEnabled()) {
            return null;
        }
        try {
            // { "contents": [ { "role": "user", "parts": [ { "text": "..." } ] } ] }
            ObjectNode content = textContent(prompt);
            content.put("role", "user");
            ObjectNode body = objectMapper.createObjectNode();
            body.putArray("contents").add(content);

            JsonNode response = post(generationModel + ":generateContent", body);
            String text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                LOGGER.warn("Gemini generation response had no text");
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            LOGGER.warn("Gemini generation failed: {}", e.getMessage());
            return null;
        }
    }

    // { "parts": [ { "text": "..." } ] }
    private ObjectNode textContent(String text) {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", text);
        return content;
    }

    private JsonNode post(String path, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)   // the key is sent in a header, never logged
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/GeminiService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/SupportAgentService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.util.List;
import java.util.Optional;

import com.examly.springapp.model.SupportAgent;

public interface SupportAgentService {

    SupportAgent addSupportAgent(SupportAgent supportAgent);

    Optional<SupportAgent> getSupportAgentById(Long agentId);

    List<SupportAgent> getAllSupportAgents();

    SupportAgent updateSupportAgent(Long agentId, SupportAgent supportAgent);

    SupportAgent deleteSupportAgent(Long agentId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/SupportAgentService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/TicketService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.util.List;
import java.util.Optional;

import com.examly.springapp.model.Ticket;

public interface TicketService {

    Ticket addTicket(Ticket ticket);

    Optional<Ticket> getTicketById(Long ticketId);

    List<Ticket> getAllTickets();

    Ticket updateTicket(Long ticketId, Ticket ticket);

    Ticket deleteTicket(Long ticketId);

    List<Ticket> getTicketsByAgentId(Long agentId);

    List<Ticket> getTicketsByUserId(Long userId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/TicketService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service"
cat > "$APP/src/main/java/com/examly/springapp/service/UserService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;

public interface UserService {

    User createUser(User user);

    LoginDTO loginUser(User user);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/UserService.java"

# =====================================================================================
# 2. SERVICE IMPLEMENTATIONS  (service/impl/)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/service/impl"
cat > "$APP/src/main/java/com/examly/springapp/service/impl/FeedbackServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.FeedbackService;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepo feedbackRepo;
    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;

    public FeedbackServiceImpl(FeedbackRepo feedbackRepo, TicketRepo ticketRepo, UserRepo userRepo) {
        this.feedbackRepo = feedbackRepo;
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
    }

    @Override
    public Feedback createFeedback(Feedback feedback) {
        if (feedback.getTicket() == null || feedback.getTicket().getTicketId() == null) {
            throw new IllegalArgumentException("Please select the ticket you are reviewing");
        }

        Ticket ticket = ticketRepo.findById(feedback.getTicket().getTicketId()).orElse(null);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found");
        }
        User user = userRepo.findById(feedback.getUser().getUserId()).orElse(null);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        // A client can only review their own ticket
        if (!ticket.getUser().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("You can only give feedback on your own tickets");
        }
        // Feedback is allowed only after the ticket is Resolved or Closed
        if (!"Resolved".equals(ticket.getStatus()) && !"Closed".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("Feedback can be given only for a Resolved or Closed ticket");
        }
        // One feedback per ticket
        if (!feedbackRepo.findByTicketTicketId(ticket.getTicketId()).isEmpty()) {
            throw new IllegalStateException("You have already given feedback for this ticket");
        }

        feedback.setFeedbackId(null);
        feedback.setUser(user);
        feedback.setTicket(ticket);
        feedback.setSupportAgent(ticket.getSupportAgent());
        feedback.setDate(LocalDate.now());
        return feedbackRepo.save(feedback);
    }

    @Override
    public Feedback getFeedbackById(Long feedbackId) {
        Feedback feedback = feedbackRepo.findById(feedbackId).orElse(null);
        if (feedback == null) {
            throw new NoSuchElementException("Feedback not found with id " + feedbackId);
        }
        return feedback;
    }

    @Override
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepo.findAll();
    }

    @Override
    public Feedback deleteFeedback(Long feedbackId) {
        Feedback feedback = getFeedbackById(feedbackId);
        feedbackRepo.delete(feedback);
        return feedback;
    }

    @Override
    public List<Feedback> getFeedbacksByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return feedbackRepo.findByUserUserId(userId);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/impl/FeedbackServiceImpl.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service/impl"
cat > "$APP/src/main/java/com/examly/springapp/service/impl/SupportAgentServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.examly.springapp.exceptions.AgentDeletionException;
import com.examly.springapp.exceptions.DuplicateAgentException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.service.SupportAgentService;

@Service
public class SupportAgentServiceImpl implements SupportAgentService {

    private final SupportAgentRepo supportAgentRepo;
    private final TicketRepo ticketRepo;
    private final FeedbackRepo feedbackRepo;

    public SupportAgentServiceImpl(SupportAgentRepo supportAgentRepo, TicketRepo ticketRepo,
            FeedbackRepo feedbackRepo) {
        this.supportAgentRepo = supportAgentRepo;
        this.ticketRepo = ticketRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public SupportAgent addSupportAgent(SupportAgent supportAgent) {
        String email = supportAgent.getEmail().trim().toLowerCase();

        // Two agents cannot share the same email
        if (supportAgentRepo.findByEmail(email) != null) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        supportAgent.setAgentId(null);
        supportAgent.setEmail(email);
        supportAgent.setAddedDate(LocalDate.now());
        return supportAgentRepo.save(supportAgent);
    }

    @Override
    public Optional<SupportAgent> getSupportAgentById(Long agentId) {
        return supportAgentRepo.findById(agentId);
    }

    @Override
    public List<SupportAgent> getAllSupportAgents() {
        return supportAgentRepo.findAll();
    }

    @Override
    public SupportAgent updateSupportAgent(Long agentId, SupportAgent supportAgent) {
        SupportAgent existing = supportAgentRepo.findById(agentId).orElse(null);
        if (existing == null) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }

        String email = supportAgent.getEmail().trim().toLowerCase();
        SupportAgent sameEmail = supportAgentRepo.findByEmail(email);
        if (sameEmail != null && !sameEmail.getAgentId().equals(agentId)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        existing.setName(supportAgent.getName());
        existing.setEmail(email);
        existing.setPhone(supportAgent.getPhone());
        existing.setExpertise(supportAgent.getExpertise());
        existing.setExperience(supportAgent.getExperience());
        existing.setStatus(supportAgent.getStatus());
        existing.setProfile(supportAgent.getProfile());
        existing.setShiftTiming(supportAgent.getShiftTiming());
        existing.setRemarks(supportAgent.getRemarks());
        // addedDate never changes after the agent is created
        return supportAgentRepo.save(existing);
    }

    @Override
    public SupportAgent deleteSupportAgent(Long agentId) {
        SupportAgent agent = supportAgentRepo.findById(agentId).orElse(null);
        if (agent == null) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }

        // An agent that worked on tickets (or received feedback) is kept for the ticket history
        if (!ticketRepo.findBySupportAgentAgentId(agentId).isEmpty()
                || !feedbackRepo.findBySupportAgentAgentId(agentId).isEmpty()) {
            throw new AgentDeletionException(
                    "This agent is assigned to tickets and cannot be deleted. Mark the agent Unavailable instead.");
        }

        supportAgentRepo.delete(agent);
        return agent;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/impl/SupportAgentServiceImpl.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service/impl"
cat > "$APP/src/main/java/com/examly/springapp/service/impl/TicketServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.TicketService;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;
    private final SupportAgentRepo supportAgentRepo;
    private final FeedbackRepo feedbackRepo;

    public TicketServiceImpl(TicketRepo ticketRepo, UserRepo userRepo, SupportAgentRepo supportAgentRepo,
            FeedbackRepo feedbackRepo) {
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.supportAgentRepo = supportAgentRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public Ticket addTicket(Ticket ticket) {
        User user = userRepo.findById(ticket.getUser().getUserId()).orElse(null);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        String title = ticket.getTitle().trim();
        if (titleAlreadyUsed(user.getUserId(), title, null)) {
            throw new DuplicateTicketException("A ticket with this title already exists");
        }

        // Every new ticket starts as Open, without an agent and without a resolution
        ticket.setTicketId(null);
        ticket.setTitle(title);
        ticket.setUser(user);
        ticket.setStatus("Open");
        ticket.setCreatedDate(LocalDate.now());
        ticket.setResolutionDate(null);
        ticket.setResolutionSummary(null);
        ticket.setSatisfied(null);
        ticket.setSupportAgent(null);
        return ticketRepo.save(ticket);
    }

    @Override
    public Optional<Ticket> getTicketById(Long ticketId) {
        return ticketRepo.findById(ticketId);
    }

    @Override
    public List<Ticket> getAllTickets() {
        return ticketRepo.findAll();
    }

    @Override
    public Ticket updateTicket(Long ticketId, Ticket ticket) {
        Ticket existing = ticketRepo.findById(ticketId).orElse(null);
        if (existing == null) {
            throw new NoSuchElementException("Ticket not found with id " + ticketId);
        }

        // 1. Ticket details can only change while the ticket is Open and no agent is assigned
        boolean detailsChanged = !existing.getTitle().equals(ticket.getTitle().trim())
                || !existing.getDescription().equals(ticket.getDescription())
                || !existing.getPriority().equals(ticket.getPriority())
                || !existing.getIssueCategory().equals(ticket.getIssueCategory());
        if (detailsChanged) {
            if (!"Open".equals(existing.getStatus()) || existing.getSupportAgent() != null) {
                throw new IllegalArgumentException(
                        "A ticket can only be edited while it is Open and no agent is assigned");
            }
            String title = ticket.getTitle().trim();
            if (titleAlreadyUsed(existing.getUser().getUserId(), title, ticketId)) {
                throw new DuplicateTicketException("A ticket with this title already exists");
            }
            existing.setTitle(title);
            existing.setDescription(ticket.getDescription());
            existing.setPriority(ticket.getPriority());
            existing.setIssueCategory(ticket.getIssueCategory());
        }

        // 2. Assign a support agent (only an Available agent can be assigned)
        if (ticket.getSupportAgent() != null && ticket.getSupportAgent().getAgentId() != null) {
            Long newAgentId = ticket.getSupportAgent().getAgentId();
            SupportAgent currentAgent = existing.getSupportAgent();
            if (currentAgent == null || !currentAgent.getAgentId().equals(newAgentId)) {
                SupportAgent agent = supportAgentRepo.findById(newAgentId).orElse(null);
                if (agent == null) {
                    throw new NoSuchElementException("Support agent not found with id " + newAgentId);
                }
                if (!"Available".equals(agent.getStatus())) {
                    throw new IllegalArgumentException("This support agent is currently unavailable");
                }
                existing.setSupportAgent(agent);
            }
        }

        // 3. Resolution summary and satisfaction (given by the client)
        if (ticket.getResolutionSummary() != null) {
            existing.setResolutionSummary(ticket.getResolutionSummary().trim());
        }
        if (ticket.getSatisfied() != null) {
            existing.setSatisfied(ticket.getSatisfied());
        }

        // 4. Status change
        String newStatus = ticket.getStatus();
        if (newStatus != null && !newStatus.equals(existing.getStatus())) {
            changeStatus(existing, newStatus);
        }

        return ticketRepo.save(existing);
    }

    @Override
    public Ticket deleteTicket(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId).orElse(null);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found with id " + ticketId);
        }
        if (!"Open".equals(ticket.getStatus()) || ticket.getSupportAgent() != null) {
            throw new TicketDeletionException("Only an Open ticket without an assigned agent can be deleted");
        }
        if (!feedbackRepo.findByTicketTicketId(ticketId).isEmpty()) {
            throw new TicketDeletionException("This ticket has feedback and cannot be deleted");
        }
        ticketRepo.delete(ticket);
        return ticket;
    }

    @Override
    public List<Ticket> getTicketsByAgentId(Long agentId) {
        if (!supportAgentRepo.existsById(agentId)) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }
        return ticketRepo.findBySupportAgentAgentId(agentId);
    }

    @Override
    public List<Ticket> getTicketsByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return ticketRepo.findByUserUserId(userId);
    }

    // Ticket lifecycle: Open -> (agent assigned) -> Resolved (by client, needs summary) -> Closed (by manager)
    private void changeStatus(Ticket ticket, String newStatus) {
        String currentStatus = ticket.getStatus();

        if ("Resolved".equals(currentStatus) || "Closed".equals(currentStatus)) {
            if (!("Resolved".equals(currentStatus) && "Closed".equals(newStatus))) {
                throw new IllegalArgumentException("A resolved or closed ticket cannot be reopened");
            }
        }

        if ("Resolved".equals(newStatus)) {
            if (ticket.getResolutionSummary() == null || ticket.getResolutionSummary().isBlank()) {
                throw new IllegalArgumentException("Please provide resolution details before marking resolve.");
            }
            if (ticket.getSupportAgent() == null) {
                throw new IllegalArgumentException("A support agent must be assigned before resolving the ticket");
            }
            ticket.setResolutionDate(LocalDate.now());
        }

        if ("Closed".equals(newStatus) && !"Resolved".equals(currentStatus)) {
            throw new IllegalArgumentException("Only a resolved ticket can be closed");
        }

        ticket.setStatus(newStatus);
    }

    // True when the same client already has another ticket with this title.
    private boolean titleAlreadyUsed(Long userId, String title, Long ignoreTicketId) {
        List<Ticket> tickets = ticketRepo.findByUserUserId(userId);
        for (Ticket t : tickets) {
            boolean sameTicket = ignoreTicketId != null && ignoreTicketId.equals(t.getTicketId());
            if (!sameTicket && t.getTitle().equalsIgnoreCase(title)) {
                return true;
            }
        }
        return false;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/impl/TicketServiceImpl.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/service/impl"
cat > "$APP/src/main/java/com/examly/springapp/service/impl/UserServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public UserServiceImpl(UserRepo userRepo, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public User createUser(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // Check whether the email already exists (answered with 409 by GlobalExceptionHandler)
        if (userRepo.findByEmail(email) != null) {
            throw new IllegalStateException("A user with this email already exists");
        }

        user.setUserId(null);
        user.setEmail(email);
        user.setUsername(user.getUsername().trim());
        // Never store the plain password: save the BCrypt hash instead
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepo.save(user);
    }

    @Override
    public LoginDTO loginUser(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // AuthenticationManager -> DaoAuthenticationProvider -> MyUserDetailsService -> PasswordEncoder.matches()
        // Wrong email or password throws BadCredentialsException (answered with 401).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, user.getPassword()));

        UserPrinciple principle = (UserPrinciple) authentication.getPrincipal();
        String token = jwtUtils.generateToken(principle);

        return new LoginDTO(token, principle.getDisplayName(), principle.getUserRole(), principle.getUserId());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/service/impl/UserServiceImpl.java"

# =====================================================================================
# 3. MODELS  (needed by the services)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/ChatMessage.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/ChatMessage.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/ChatRequest.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/ChatRequest.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/ChatResponse.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/ChatResponse.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/EmbeddingConverter.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Stores a float[] embedding vector as text, for example "0.12,-0.5,0.33".
@Converter
public class EmbeddingConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/EmbeddingConverter.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/ErrorLog.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Every handled error is saved in the separate "ErrorLogs" table (SRS requirement).
@Entity
@Table(name = "ErrorLogs")
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long errorId;

    private LocalDateTime timestamp;

    private Integer status;

    @Column(length = 1000)
    private String message;

    @Column(length = 500)
    private String path;

    private String exceptionType;

    public ErrorLog() {
    }

    public ErrorLog(Integer status, String message, String path, String exceptionType) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.path = path;
        this.exceptionType = exceptionType;
    }

    public Long getErrorId() {
        return errorId;
    }

    public void setErrorId(Long errorId) {
        this.errorId = errorId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/ErrorLog.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/Faq.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

// One entry of faqs.json. Only used to read the seed file.
public class Faq {

    private Long id;
    private String category;
    private String question;
    private String answer;

    public Faq() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/Faq.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/FaqEntity.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

// One FAQ of the chatbot knowledge base (table: faqs).
@Entity
@Table(name = "faqs")
public class FaqEntity {

    // The id comes from faqs.json, so it is not auto-generated
    @Id
    private Long id;

    private String category;

    @Column(length = 500)
    private String question;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answer;

    // Semantic vector from Gemini, stored as text. Never sent to the browser.
    @JsonIgnore
    @Column(columnDefinition = "LONGTEXT")
    @Convert(converter = EmbeddingConverter.class)
    private float[] embedding;

    public FaqEntity() {
    }

    public FaqEntity(Long id, String category, String question, String answer) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.answer = answer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/FaqEntity.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/Feedback.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feedbackId;

    @NotBlank(message = "Feedback text is required")
    @Size(max = 1000, message = "Feedback must not exceed 1000 characters")
    @Column(nullable = false, length = 1000)
    private String feedbackText;

    // "date" is a reserved word in some databases, so the column gets a safer name.
    @Column(name = "feedback_date", nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = true)
    private SupportAgent supportAgent;

    @ManyToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    public Feedback() {
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public void setFeedbackText(String feedbackText) {
        this.feedbackText = feedbackText;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SupportAgent getSupportAgent() {
        return supportAgent;
    }

    public void setSupportAgent(SupportAgent supportAgent) {
        this.supportAgent = supportAgent;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/Feedback.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/LoginDTO.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

// Sent back to Angular after a successful login (SRS: LoginDTO).
public class LoginDTO {

    private String token;
    private String username;
    private String userRole;
    private Long userId;

    public LoginDTO() {
    }

    public LoginDTO(String token, String username, String userRole, Long userId) {
        this.token = token;
        this.username = username;
        this.userRole = userRole;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/LoginDTO.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/SupportAgent.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "support_agent")
public class SupportAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long agentId;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\d{10}$", message = "Phone must be 10 digits")
    @Column(nullable = false)
    private String phone;

    @NotBlank(message = "Expertise is required")
    private String expertise;

    @NotBlank(message = "Experience is required")
    private String experience;

    // Allowed values: Available, Unavailable.
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(Available|Unavailable)$", message = "Status must be Available or Unavailable")
    private String status;

    private LocalDate addedDate;

    // Profile / resume stored as a base64-encoded string in a large-object column. Optional.
    @Lob
    @Column(nullable = true)
    private String profile;

    @NotBlank(message = "Shift timing is required")
    private String shiftTiming;

    @NotBlank(message = "Remarks are required")
    @Column(length = 1000)
    private String remarks;

    public SupportAgent() {
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getAddedDate() {
        return addedDate;
    }

    public void setAddedDate(LocalDate addedDate) {
        this.addedDate = addedDate;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getShiftTiming() {
        return shiftTiming;
    }

    public void setShiftTiming(String shiftTiming) {
        this.shiftTiming = shiftTiming;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/SupportAgent.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/Ticket.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must not exceed 120 characters")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(nullable = false, length = 2000)
    private String description;

    @NotBlank(message = "Priority is required")
    @Pattern(regexp = "^(High|Medium|Low)$", message = "Priority must be High, Medium or Low")
    @Column(nullable = false)
    private String priority;

    // Allowed values: Open, In Progress, Resolved, Closed. Every new ticket starts as Open.
    @Pattern(regexp = "^(Open|In Progress|Resolved|Closed)$",
            message = "Status must be Open, In Progress, Resolved or Closed")
    @Column(nullable = false)
    private String status = "Open";

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = true)
    private LocalDate resolutionDate;

    @NotBlank(message = "Issue category is required")
    private String issueCategory;

    // Optional at first, but required before the ticket can be marked Resolved.
    @Column(nullable = true, length = 2000)
    private String resolutionSummary;

    @Column(nullable = true)
    private Boolean satisfied;

    // The client who raised the ticket.
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The support agent assigned by the manager (empty until assigned).
    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = true)
    private SupportAgent supportAgent;

    public Ticket() {
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }

    public void setResolutionDate(LocalDate resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public String getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(String issueCategory) {
        this.issueCategory = issueCategory;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public void setResolutionSummary(String resolutionSummary) {
        this.resolutionSummary = resolutionSummary;
    }

    public Boolean getSatisfied() {
        return satisfied;
    }

    public void setSatisfied(Boolean satisfied) {
        this.satisfied = satisfied;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SupportAgent getSupportAgent() {
        return supportAgent;
    }

    public void setSupportAgent(SupportAgent supportAgent) {
        this.supportAgent = supportAgent;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/Ticket.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/model"
cat > "$APP/src/main/java/com/examly/springapp/model/User.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    // The password can be sent to the API (register / login) but is never sent back in a response.
    // Only the BCrypt-encoded value is stored in the database.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Username is required")
    @Column(nullable = false)
    private String username;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Mobile number must be 10 digits")
    @Column(nullable = false)
    private String mobileNumber;

    @NotBlank(message = "User role is required")
    @Pattern(regexp = "^(Manager|Client)$", message = "User role must be Manager or Client")
    @Column(nullable = false)
    private String userRole;

    public User() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/model/User.java"

# =====================================================================================
# 4. REPOSITORIES  (needed by the services)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/ChatMessageRepository.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // GET /api/chat/history/{sessionId} - oldest message first
    List<ChatMessage> findBySessionIdOrderByIdAsc(String sessionId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/ChatMessageRepository.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/ErrorLogRepo.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.ErrorLog;

public interface ErrorLogRepo extends JpaRepository<ErrorLog, Long> {
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/ErrorLogRepo.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/FaqRepository.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.FaqEntity;

public interface FaqRepository extends JpaRepository<FaqEntity, Long> {
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/FaqRepository.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/FeedbackRepo.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Feedback;

public interface FeedbackRepo extends JpaRepository<Feedback, Long> {

    // GET /api/feedback/user/{userId}
    List<Feedback> findByUserUserId(Long userId);

    // Used before deleting a ticket or an agent that still has feedback.
    List<Feedback> findByTicketTicketId(Long ticketId);

    List<Feedback> findBySupportAgentAgentId(Long agentId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/FeedbackRepo.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/SupportAgentRepo.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.SupportAgent;

public interface SupportAgentRepo extends JpaRepository<SupportAgent, Long> {

    // Used to throw DuplicateAgentException when the email is already taken.
    SupportAgent findByEmail(String email);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/SupportAgentRepo.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/TicketRepo.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Ticket;

public interface TicketRepo extends JpaRepository<Ticket, Long> {

    // GET /api/ticket/user/{userId}
    List<Ticket> findByUserUserId(Long userId);

    // GET /api/ticket/agent/{agentId}
    List<Ticket> findBySupportAgentAgentId(Long agentId);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/TicketRepo.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/repository"
cat > "$APP/src/main/java/com/examly/springapp/repository/UserRepo.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.User;

public interface UserRepo extends JpaRepository<User, Long> {

    // Used by login (MyUserDetailsService) and by the duplicate-email check on register.
    User findByEmail(String email);
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/repository/UserRepo.java"

# =====================================================================================
# 5. EXCEPTIONS  (thrown by the services)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/exceptions"
cat > "$APP/src/main/java/com/examly/springapp/exceptions/AgentDeletionException.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.exceptions;

// Thrown when deleting a support agent fails (for example, the agent still has tickets).
public class AgentDeletionException extends RuntimeException {

    public AgentDeletionException(String message) {
        super(message);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/exceptions/AgentDeletionException.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/exceptions"
cat > "$APP/src/main/java/com/examly/springapp/exceptions/DuplicateAgentException.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.exceptions;

// Thrown when a support agent with the same email already exists.
public class DuplicateAgentException extends RuntimeException {

    public DuplicateAgentException(String message) {
        super(message);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/exceptions/DuplicateAgentException.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/exceptions"
cat > "$APP/src/main/java/com/examly/springapp/exceptions/DuplicateTicketException.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.exceptions;

// Thrown when a client raises a ticket with a title they have already used.
public class DuplicateTicketException extends RuntimeException {

    public DuplicateTicketException(String message) {
        super(message);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/exceptions/DuplicateTicketException.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/exceptions"
cat > "$APP/src/main/java/com/examly/springapp/exceptions/GlobalExceptionHandler.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.exceptions;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;

// Turns exceptions into friendly JSON error responses and saves every error in the ErrorLogs table.
//
//   400 Bad Request  - validation errors, IllegalArgumentException (a business rule was broken)
//   401 Unauthorized - wrong email or password
//   403 Forbidden    - AccessDeniedException (for example, another client's ticket)
//   404 Not Found    - NoSuchElementException
//   409 Conflict     - duplicate data or a delete that is not allowed
//   500              - anything unexpected
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogRepo errorLogRepo;

    public GlobalExceptionHandler(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        // Collect one message per invalid field, for example {"title": "Title is required"}
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String firstMessage = "Please fill in all required fields correctly.";
        if (!fieldErrors.isEmpty()) {
            firstMessage = fieldErrors.values().iterator().next();
        }

        ResponseEntity<Map<String, Object>> response = buildResponse(HttpStatus.BAD_REQUEST, firstMessage,
                request, ex);
        response.getBody().put("errors", fieldErrors);
        return response;
    }

    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (ex instanceof HttpMessageNotReadableException) {
            message = "The request body is not valid";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleLoginFailure(AuthenticationException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, ex);
    }

    @ExceptionHandler({ DuplicateAgentException.class, DuplicateTicketException.class,
            AgentDeletionException.class, TicketDeletionException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "This data conflicts with an existing record", request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOtherErrors(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected error on {}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.",
                request, ex);
    }

    // Saves the error in the ErrorLogs table and builds the JSON body sent to Angular.
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message,
            HttpServletRequest request, Exception ex) {
        errorLogRepo.save(new ErrorLog(status.value(), message, request.getRequestURI(),
                ex.getClass().getSimpleName()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/exceptions/GlobalExceptionHandler.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/exceptions"
cat > "$APP/src/main/java/com/examly/springapp/exceptions/TicketDeletionException.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.exceptions;

// Thrown when a ticket cannot be deleted (for example, an agent is already assigned).
public class TicketDeletionException extends RuntimeException {

    public TicketDeletionException(String message) {
        super(message);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/exceptions/TicketDeletionException.java"

# =====================================================================================
# 6. CONFIG - security / JWT  (UserServiceImpl uses JwtUtils, UserPrinciple, AuthenticationManager)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/CorsConfig.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Allows the Angular app (port 8081) to call this API (port 8080).
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/CorsConfig.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/JwtAccessDeniedHandler.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Sends 403 Forbidden when a logged-in user calls a URL that their role is not allowed to use.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorLogRepo errorLogRepo;

    public JwtAccessDeniedHandler(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        String message = "You are not allowed to access this resource";
        errorLogRepo.save(new ErrorLog(403, message, request.getRequestURI(), "Forbidden"));

        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"" + message + "\"}");
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/JwtAccessDeniedHandler.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/JwtAuthenticationEntryPoint.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Sends 401 Unauthorized when a protected URL is called without a valid JWT.
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorLogRepo errorLogRepo;

    public JwtAuthenticationEntryPoint(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = "Please login to access this resource";
        errorLogRepo.save(new ErrorLog(401, message, request.getRequestURI(), "Unauthorized"));

        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/JwtAuthenticationEntryPoint.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/JwtAuthenticationFilter.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs once for every request.
// If the request has "Authorization: Bearer <token>" and the token is valid,
// the user is loaded and stored in the SecurityContext for this request only.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final MyUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, MyUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.getEmailFromToken(token);
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // Create an authenticated Authentication and put it in the SecurityContext
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    // The user in the token no longer exists: continue without authentication
                    SecurityContextHolder.clearContext();
                }
            }
        }

        // Missing or invalid token: no authentication is set, so protected URLs answer 401.
        filterChain.doFilter(request, response);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/JwtAuthenticationFilter.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/JwtUtils.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// Creates and validates JSON Web Tokens.
@Component
public class JwtUtils {

    private final Key key;
    private final long expirationMs;

    public JwtUtils(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    // Called after a successful login. The email is the token subject.
    public String generateToken(UserPrinciple user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getUserId())
                .claim("role", user.getUserRole())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Returns the email stored in the token.
    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    // A token is valid when the signature is correct and it has not expired.
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/JwtUtils.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/MyUserDetailsService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;

// Step 5 of the DAO authentication flow: load the user from the database.
// This class only LOADS the user. DaoAuthenticationProvider checks the password
// with PasswordEncoder.matches().
@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    public MyUserDetailsService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("Invalid email or password");
        }
        return new UserPrinciple(user);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/MyUserDetailsService.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/SecurityConfig.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * Login (DAO authentication, see the Spring Security PPT):
 *   POST /api/login -> AuthenticationManager -> DaoAuthenticationProvider
 *   -> MyUserDetailsService -> UserRepo -> PasswordEncoder.matches() -> JWT
 *
 * Every other request:
 *   Authorization: Bearer <JWT> -> JwtAuthenticationFilter -> SecurityContext -> role rules below
 *
 * No server session is created (STATELESS). CSRF is disabled because the API uses a JWT
 * in the Authorization header instead of cookies.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final MyUserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    // SRS "Platform Prerequisites": the evaluation platform may need GET /api/ticket and
    // GET /api/feedback without a token. Keep this false for normal use.
    @Value("${app.security.public-read-endpoints:false}")
    private boolean publicReadEndpoints;

    public SecurityConfig(MyUserDetailsService userDetailsService, JwtUtils jwtUtils,
            JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // Passwords are stored as BCrypt hashes, never as plain text.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Loads the user with MyUserDetailsService and checks the password with the PasswordEncoder.
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // Coordinates authentication by delegating to the DaoAuthenticationProvider.
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(daoAuthenticationProvider());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtils, userDetailsService);

        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        http.authorizeHttpRequests(auth -> {
            // Public URLs
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/api/register", "/api/login").permitAll();
            auth.requestMatchers("/error").permitAll();
            // Phase 2 FAQ chatbot: usable without logging in
            auth.requestMatchers("/api/chat", "/api/chat/**", "/api/faqs").permitAll();

            if (publicReadEndpoints) {
                auth.requestMatchers(HttpMethod.GET, "/api/ticket", "/api/feedback").permitAll();
            }

            // Tickets
            auth.requestMatchers(HttpMethod.POST, "/api/ticket").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/user/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/agent/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/*").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.PUT, "/api/ticket/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.DELETE, "/api/ticket/*").hasRole("CLIENT");

            // Support agents
            auth.requestMatchers(HttpMethod.POST, "/api/supportAgent").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.GET, "/api/supportAgent").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.GET, "/api/supportAgent/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.PUT, "/api/supportAgent/*").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.DELETE, "/api/supportAgent/*").hasRole("MANAGER");

            // Feedback
            auth.requestMatchers(HttpMethod.POST, "/api/feedback").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback/user/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.DELETE, "/api/feedback/*").hasRole("CLIENT");

            // Anything else needs a logged-in user
            auth.anyRequest().authenticated();
        });

        http.authenticationProvider(daoAuthenticationProvider());
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/SecurityConfig.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/config"
cat > "$APP/src/main/java/com/examly/springapp/config/UserPrinciple.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.examly.springapp.model.User;

// The logged-in user as Spring Security sees it.
// The email is used as the "username" for login, and the role "Manager"/"Client"
// becomes the authority "ROLE_MANAGER"/"ROLE_CLIENT".
public class UserPrinciple implements UserDetails {

    private Long userId;
    private String email;
    private String password;
    private String username;
    private String userRole;
    private List<GrantedAuthority> authorities;

    public UserPrinciple(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.username = user.getUsername();
        this.userRole = user.getUserRole();
        this.authorities = new ArrayList<>();
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().toUpperCase()));
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    // The display name of the user (for example "Rahul Verma").
    public String getDisplayName() {
        return username;
    }

    public String getUserRole() {
        return userRole;
    }

    public boolean isManager() {
        return "Manager".equals(userRole);
    }

    public boolean isClient() {
        return "Client".equals(userRole);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // Spring Security calls this the username; in SupportSphere users log in with their email.
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/config/UserPrinciple.java"

# =====================================================================================
# 7. CONTROLLERS  (call the services)
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp/controller"
cat > "$APP/src/main/java/com/examly/springapp/controller/AuthController.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // 201 with the saved user, 400 for invalid data, 409 if the email already exists
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody User user) {
        User savedUser = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    // 201 with LoginDTO (token, username, userRole, userId), 401 for invalid credentials
    @PostMapping("/login")
    public ResponseEntity<LoginDTO> loginUser(@RequestBody User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        LoginDTO loginDTO = userService.loginUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(loginDTO);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/controller/AuthController.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/controller"
cat > "$APP/src/main/java/com/examly/springapp/controller/ChatController.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/controller/ChatController.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/controller"
cat > "$APP/src/main/java/com/examly/springapp/controller/FeedbackController.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.User;
import com.examly.springapp.service.FeedbackService;

import jakarta.validation.Valid;

// URL role rules are in SecurityConfig. A Client can only see and delete their own feedback.
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    // Client: 201 with the new feedback
    @PostMapping
    public ResponseEntity<Feedback> createFeedback(@Valid @RequestBody Feedback feedback,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        // The feedback always belongs to the logged-in client
        User owner = new User();
        owner.setUserId(currentUser.getUserId());
        feedback.setUser(owner);

        Feedback savedFeedback = feedbackService.createFeedback(feedback);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFeedback);
    }

    // Manager: any feedback. Client: only their own.
    @GetMapping("/{feedbackId}")
    public ResponseEntity<Feedback> getFeedbackById(@PathVariable Long feedbackId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        if (currentUser.isClient()) {
            checkOwner(feedback, currentUser);
        }
        return ResponseEntity.ok(feedback);
    }

    // Manager: all feedback. Client: only their own. 204 when there is none.
    @GetMapping
    public ResponseEntity<List<Feedback>> getAllFeedbacks(@AuthenticationPrincipal UserPrinciple currentUser) {
        List<Feedback> feedbacks;
        if (currentUser != null && currentUser.isClient()) {
            feedbacks = feedbackService.getFeedbacksByUserId(currentUser.getUserId());
        } else {
            feedbacks = feedbackService.getAllFeedbacks();
        }

        if (feedbacks.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    // Client: their own feedback only
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Feedback>> getFeedbacksByUserId(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        if (!userId.equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only view your own feedback");
        }

        List<Feedback> feedbacks = feedbackService.getFeedbacksByUserId(userId);
        if (feedbacks.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    // Client: 200 with the deleted feedback
    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Feedback> deleteFeedback(@PathVariable Long feedbackId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        checkOwner(feedback, currentUser);

        Feedback deletedFeedback = feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.ok(deletedFeedback);
    }

    // Throws 403 when a client tries to use somebody else's feedback.
    private void checkOwner(Feedback feedback, UserPrinciple currentUser) {
        if (!feedback.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only access your own feedback");
        }
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/controller/FeedbackController.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/controller"
cat > "$APP/src/main/java/com/examly/springapp/controller/SupportAgentController.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.service.SupportAgentService;

import jakarta.validation.Valid;

// Role rules (Manager only, except GET by id) are in SecurityConfig.
@RestController
@RequestMapping("/api/supportAgent")
public class SupportAgentController {

    private final SupportAgentService supportAgentService;

    public SupportAgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    // Manager: 201 with the new agent, 409 if the email is already used
    @PostMapping
    public ResponseEntity<SupportAgent> addSupportAgent(@Valid @RequestBody SupportAgent supportAgent) {
        SupportAgent savedAgent = supportAgentService.addSupportAgent(supportAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAgent);
    }

    // Manager and Client: 200 with the agent, 404 if not found
    @GetMapping("/{agentId}")
    public ResponseEntity<SupportAgent> getSupportAgentById(@PathVariable Long agentId) {
        SupportAgent agent = supportAgentService.getSupportAgentById(agentId).orElse(null);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agent);
    }

    // Manager: 200 with all agents, 204 when there are none
    @GetMapping
    public ResponseEntity<List<SupportAgent>> getAllSupportAgents() {
        List<SupportAgent> agents = supportAgentService.getAllSupportAgents();
        if (agents.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(agents);
    }

    // Manager: 200 with the updated agent
    @PutMapping("/{agentId}")
    public ResponseEntity<SupportAgent> updateSupportAgent(@PathVariable Long agentId,
            @Valid @RequestBody SupportAgent supportAgent) {
        SupportAgent updatedAgent = supportAgentService.updateSupportAgent(agentId, supportAgent);
        return ResponseEntity.ok(updatedAgent);
    }

    // Manager: 200 with the deleted agent
    @DeleteMapping("/{agentId}")
    public ResponseEntity<SupportAgent> deleteSupportAgent(@PathVariable Long agentId) {
        SupportAgent deletedAgent = supportAgentService.deleteSupportAgent(agentId);
        return ResponseEntity.ok(deletedAgent);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/controller/SupportAgentController.java"

mkdir -p "$APP/src/main/java/com/examly/springapp/controller"
cat > "$APP/src/main/java/com/examly/springapp/controller/TicketController.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.service.TicketService;

import jakarta.validation.Valid;

// The URL role rules are in SecurityConfig. This controller adds the ownership checks:
// a Client can only see and change their own tickets (the userId in the URL is never trusted).
@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Client: 201 with the new ticket
    @PostMapping
    public ResponseEntity<Ticket> addTicket(@Valid @RequestBody Ticket ticket,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        // The ticket always belongs to the logged-in client
        User owner = new User();
        owner.setUserId(currentUser.getUserId());
        ticket.setUser(owner);

        Ticket savedTicket = ticketService.addTicket(ticket);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTicket);
    }

    // Client: 200 with the ticket, 404 if not found
    @GetMapping("/{ticketId}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long ticketId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket ticket = ticketService.getTicketById(ticketId).orElse(null);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        checkOwner(ticket, currentUser);
        return ResponseEntity.ok(ticket);
    }

    // Manager: every ticket. Client: only their own tickets. 204 when there are none.
    @GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets(@AuthenticationPrincipal UserPrinciple currentUser) {
        List<Ticket> tickets;
        if (currentUser != null && currentUser.isClient()) {
            tickets = ticketService.getTicketsByUserId(currentUser.getUserId());
        } else {
            tickets = ticketService.getAllTickets();
        }

        if (tickets.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tickets);
    }

    // Manager: assign an agent or close the ticket.
    // Client: edit their Open ticket, add the resolution summary, mark it Resolved.
    @PutMapping("/{ticketId}")
    public ResponseEntity<Ticket> updateTicket(@PathVariable Long ticketId, @Valid @RequestBody Ticket ticket,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket existing = ticketService.getTicketById(ticketId).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        if (currentUser.isManager()) {
            // A manager only assigns agents and changes the status, so keep the client's fields as they are
            ticket.setTitle(existing.getTitle());
            ticket.setDescription(existing.getDescription());
            ticket.setPriority(existing.getPriority());
            ticket.setIssueCategory(existing.getIssueCategory());
            ticket.setResolutionSummary(existing.getResolutionSummary());
            ticket.setSatisfied(existing.getSatisfied());
        } else {
            checkOwner(existing, currentUser);
            // A client cannot assign an agent, close a ticket or set it to In Progress
            ticket.setSupportAgent(existing.getSupportAgent());
            String newStatus = ticket.getStatus();
            boolean statusChanged = newStatus != null && !newStatus.equals(existing.getStatus());
            if (statusChanged && ("Closed".equals(newStatus) || "In Progress".equals(newStatus))) {
                throw new AccessDeniedException("Only a manager can change the ticket to " + newStatus);
            }
        }

        Ticket updatedTicket = ticketService.updateTicket(ticketId, ticket);
        return ResponseEntity.ok(updatedTicket);
    }

    // Client: 200 with the deleted ticket
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Ticket> deleteTicket(@PathVariable Long ticketId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket existing = ticketService.getTicketById(ticketId).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        checkOwner(existing, currentUser);

        Ticket deletedTicket = ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok(deletedTicket);
    }

    // Client: their own tickets only
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Ticket>> getTicketsByUserId(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        if (!userId.equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only view your own tickets");
        }
        return ResponseEntity.ok(ticketService.getTicketsByUserId(userId));
    }

    // Client: their own tickets that were handled by this agent ("Tickets Worked")
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<List<Ticket>> getTicketsByAgentId(@PathVariable Long agentId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        List<Ticket> agentTickets = ticketService.getTicketsByAgentId(agentId);

        List<Ticket> myTickets = new ArrayList<>();
        for (Ticket ticket : agentTickets) {
            if (ticket.getUser().getUserId().equals(currentUser.getUserId())) {
                myTickets.add(ticket);
            }
        }
        return ResponseEntity.ok(myTickets);
    }

    // Throws 403 when a client tries to use somebody else's ticket.
    private void checkOwner(Ticket ticket, UserPrinciple currentUser) {
        if (!ticket.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only access your own tickets");
        }
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/controller/TicketController.java"

# =====================================================================================
# 8. APPLICATION CLASS
# =====================================================================================

mkdir -p "$APP/src/main/java/com/examly/springapp"
cat > "$APP/src/main/java/com/examly/springapp/SpringappApplication.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringappApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringappApplication.class, args);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/main/java/com/examly/springapp/SpringappApplication.java"

# =====================================================================================
# 9. pom.xml + RESOURCES  (application.properties, faqs.json)
# =====================================================================================

cat > "$APP/pom.xml" <<'SUPPORTSPHERE_EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.0.1</version>
        <relativePath/>
    </parent>

    <groupId>com.examly</groupId>
    <artifactId>springapp</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>springapp</name>
    <description>SupportSphere - Support ticket management platform</description>

    <properties>
        <java.version>17</java.version>
        <jjwt.version>0.11.5</jjwt.version>
    </properties>

    <dependencies>
        <!-- Web / REST -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Persistence -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Security + JWT -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>


        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
SUPPORTSPHERE_EOF
echo "  wrote pom.xml"

mkdir -p "$APP/src/main/resources"
cat > "$APP/src/main/resources/application.properties" <<'SUPPORTSPHERE_EOF'
server.port=8080

# ---------- MySQL (database: appdb) ----------
spring.datasource.url=jdbc:mysql://localhost:3306/appdb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:examly}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# ---------- JWT ----------
# Base64 secret (at least 256 bits). Set JWT_SECRET in production; the default is for local development only.
jwt.secret=${JWT_SECRET:U3VwcG9ydFNwaGVyZURldk9ubHlTZWNyZXRLZXlDaGFuZ2VNZUluUHJvZHVjdGlvbjEyMzQ1Njc4OTA=}
# Token lifetime in milliseconds (24 hours)
jwt.expiration=${JWT_EXPIRATION:86400000}

# ---------- CORS (the Angular app) ----------
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:8081}

# ---------- Auto-evaluation ----------
# SRS "Platform Prerequisites": set to true only if the evaluation platform must call
# GET /api/ticket and GET /api/feedback without a token.
app.security.public-read-endpoints=${PUBLIC_READ_ENDPOINTS:false}

# Never send stack traces to the browser
server.error.include-stacktrace=never

# ---------- AI FAQ chatbot (Phase 2) ----------
# Set GEMINI_API_KEY to enable semantic search and Gemini answers.
# Without a key the chatbot still works, using word-overlap (lexical) matching.
gemini.api.key=${GEMINI_API_KEY:}
gemini.embedding.model=gemini-embedding-2
gemini.generation.model=gemini-2.5-flash
chatbot.similarity.threshold=0.65
chatbot.lexical.threshold=0.08
SUPPORTSPHERE_EOF
echo "  wrote src/main/resources/application.properties"

mkdir -p "$APP/src/main/resources"
cat > "$APP/src/main/resources/faqs.json" <<'SUPPORTSPHERE_EOF'
[
  {
    "id": 1,
    "category": "Tickets",
    "question": "How do I create or raise a new ticket?",
    "answer": "Log in as a Client and choose Add Ticket (or Create New Ticket on the Home page). Enter a title, a description, a priority (High, Medium or Low) and an issue category (Technical, Billing or General), then select Add Ticket. Every new ticket starts with the status Open."
  },
  {
    "id": 2,
    "category": "Tickets",
    "question": "How do I edit or delete my ticket?",
    "answer": "Open View Tickets and use the Edit or Delete button on the ticket. You can edit or delete a ticket only while it is Open and no support agent has been assigned. Once a manager assigns an agent, the ticket can no longer be changed."
  },
  {
    "id": 3,
    "category": "Tickets",
    "question": "How do I check or track my ticket status?",
    "answer": "Open View Tickets to see all your tickets with their status: Open, In Progress, Resolved or Closed. Select a ticket to see its details, the assigned agent and a timeline of its progress. You can search by title or category and filter by priority."
  },
  {
    "id": 4,
    "category": "Tickets",
    "question": "How do I mark my ticket as resolved?",
    "answer": "After an agent has worked on your ticket, open it and choose Provide Summary. Enter a resolution summary, select whether you are satisfied, and confirm. Then choose Mark as Resolved. A resolution summary is required before a ticket can be marked Resolved."
  },
  {
    "id": 5,
    "category": "Tickets",
    "question": "Who closes a ticket?",
    "answer": "Only a manager can close a ticket, and only after the client has marked it Resolved. Once a ticket is Closed, no further changes are possible, but the client can still view the resolution summary and write a review."
  },
  {
    "id": 6,
    "category": "Tickets",
    "question": "What do High, Medium and Low ticket priority levels mean?",
    "answer": "High means work is blocked or many users are affected. Medium means work is impacted but a workaround exists. Low is for questions or minor inconveniences. Choose the priority when you create the ticket."
  },
  {
    "id": 7,
    "category": "Support Agents",
    "question": "How are support agents assigned to tickets?",
    "answer": "A manager assigns agents. SupportSphere suggests available agents whose expertise matches the ticket's issue category, and the manager can also choose any available agent manually. Only agents marked Available can be assigned."
  },
  {
    "id": 8,
    "category": "Support Agents",
    "question": "Where can I see the agents who worked on my tickets?",
    "answer": "Open View Agents from the menu. It shows every support agent assigned to your tickets. Select Tickets Worked on an agent card to see the tickets that agent handled for you."
  },
  {
    "id": 9,
    "category": "Support Agents",
    "question": "How does a manager add or update a support agent?",
    "answer": "Managers choose Add Support Agent and fill in the name, email, phone, expertise, experience, shift timing, availability and remarks, with an optional profile or resume. Agents can be edited, made available or unavailable, or deleted from View Support Agents."
  },
  {
    "id": 10,
    "category": "Feedbacks",
    "question": "How do I give feedback or write a review for an agent?",
    "answer": "Once your ticket is Resolved or Closed, choose Write a Review from the ticket or from Tickets Worked on the agent's card. Select the ticket, pick a category, give a rating from 1 to 5 stars and write your comment. Each ticket can be reviewed once."
  },
  {
    "id": 11,
    "category": "Feedbacks",
    "question": "Can I view or delete feedback I posted?",
    "answer": "Yes. Open My Feedbacks to see every review you have written, with the ticket and agent details. Use the Delete button on a feedback card and confirm to remove it."
  },
  {
    "id": 12,
    "category": "Feedbacks",
    "question": "Who can see my feedback?",
    "answer": "Managers can see all client feedback, including the rating, comment, category, ticket and agent. Other clients cannot see your feedback."
  },
  {
    "id": 13,
    "category": "Account",
    "question": "How do I sign up and create an account?",
    "answer": "Choose Sign up on the login page and enter your username, email, 10-digit mobile number, role (Manager or Client) and a password of at least 8 characters with a letter, a number and a symbol. Each email can be registered only once."
  },
  {
    "id": 14,
    "category": "Account",
    "question": "Why can't I log in to my account?",
    "answer": "Check that you are using the email and password you registered with. If you see Invalid Email or Password, the email or password is incorrect. If your session has expired, simply log in again."
  },
  {
    "id": 15,
    "category": "Account",
    "question": "How do I log out?",
    "answer": "Select Log out in the sidebar, or open your profile menu in the top-right corner and choose Log out, then confirm. You will return to the login page."
  }
]
SUPPORTSPHERE_EOF
echo "  wrote src/main/resources/faqs.json"

# =====================================================================================
# 10. TESTS
# =====================================================================================

mkdir -p "$APP/src/test/java/com/examly/springapp"
cat > "$APP/src/test/java/com/examly/springapp/SpringappApplicationTests.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringappApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the whole application context (security, JPA, controllers) starts.
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/SpringappApplicationTests.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/config"
cat > "$APP/src/test/java/com/examly/springapp/config/JwtUtilsTest.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.examly.springapp.model.User;

class JwtUtilsTest {

    private static final String SECRET = "VGVzdE9ubHlTZWNyZXRGb3JTdXBwb3J0U3BoZXJlSnVuaXRUZXN0czAxMjM0NTY3ODk=";

    private UserPrinciple sampleUser() {
        User user = new User();
        user.setUserId(7L);
        user.setEmail("alice@test.com");
        user.setPassword("encoded");
        user.setUsername("Alice");
        user.setUserRole("Client");
        return new UserPrinciple(user);
    }

    @Test
    void generatedTokenIsValidAndContainsTheEmail() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 60000);
        String token = jwtUtils.generateToken(sampleUser());

        assertTrue(jwtUtils.validateToken(token));
        assertEquals("alice@test.com", jwtUtils.getEmailFromToken(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, -1000);
        String token = jwtUtils.generateToken(sampleUser());

        assertFalse(jwtUtils.validateToken(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 60000);
        String token = jwtUtils.generateToken(sampleUser()) + "x";

        assertFalse(jwtUtils.validateToken(token));
        assertFalse(jwtUtils.validateToken("not-a-token"));
    }

    @Test
    void userPrincipleMapsRoleToAuthority() {
        UserPrinciple user = sampleUser();
        assertEquals("ROLE_CLIENT", user.getAuthorities().iterator().next().getAuthority());
        assertTrue(user.isClient());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/config/JwtUtilsTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/controller"
cat > "$APP/src/test/java/com/examly/springapp/controller/ChatIntegrationTest.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Chatbot endpoints are public (no JWT); the existing APIs stay protected. Gemini is disabled in tests.
@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode chat(String message, String sessionId) throws Exception {
        String body = "{\"message\":\"" + message + "\"" + (sessionId == null ? "" : ",\"sessionId\":\"" + sessionId + "\"") + "}";
        MvcResult result = mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void faqsArePublicAndHideEmbeddings() throws Exception {
        mockMvc.perform(get("/api/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].question").exists())
                .andExpect(jsonPath("$[0].embedding").doesNotExist());
    }

    @Test
    void conversationWithFollowUpHistoryAndClear() throws Exception {
        JsonNode first = chat("How do I create a ticket?", null);
        String sessionId = first.get("sessionId").asText();
        org.junit.jupiter.api.Assertions.assertTrue(first.get("matched").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("How do I create or raise a new ticket?", first.get("matchedQuestion").asText());
        org.junit.jupiter.api.Assertions.assertEquals("lexical", first.get("source").asText());

        JsonNode second = chat("What about editing it?", sessionId);
        org.junit.jupiter.api.Assertions.assertEquals("How do I edit or delete my ticket?", second.get("matchedQuestion").asText());
        org.junit.jupiter.api.Assertions.assertTrue(second.get("resolvedQuestion").asText().contains("ticket"));

        mockMvc.perform(get("/api/chat/history/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userMessage").value("How do I create a ticket?"))
                .andExpect(jsonPath("$[1].userMessage").value("What about editing it?"));

        mockMvc.perform(delete("/api/chat/memory/" + sessionId)).andExpect(status().isNoContent());

        // The saved transcript is kept after clearing the memory
        mockMvc.perform(get("/api/chat/history/" + sessionId)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unrelatedQuestionGetsTheNoMatchReply() throws Exception {
        JsonNode response = chat("What is the weather in Paris?", null);
        org.junit.jupiter.api.Assertions.assertFalse(response.get("matched").asBoolean());
        org.junit.jupiter.api.Assertions.assertTrue(response.get("reply").asText().contains("couldn't find"));
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Message is required")));
    }

    @Test
    void chatIgnoresAnInvalidTokenButOtherApisStayProtected() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", "Bearer garbage")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"How do I log out?\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ticket").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/supportAgent")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/feedback")).andExpect(status().isUnauthorized());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/controller/ChatIntegrationTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/controller"
cat > "$APP/src/test/java/com/examly/springapp/controller/SecurityIntegrationTest.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.examly.springapp.repository.ErrorLogRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// End-to-end checks of registration, DAO login, JWT, role rules and ownership rules (H2 database).
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ErrorLogRepo errorLogRepo;

    // ---------------------------------------------------------------- helpers

    private String uniqueEmail(String name) {
        return name + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private JsonNode register(String email, String role) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Password@1\",\"username\":\"Test User\","
                + "\"mobileNumber\":\"9876543210\",\"userRole\":\"" + role + "\"}";
        MvcResult result = mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode login(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Password@1\"}";
        MvcResult result = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // Registers a user and returns "Bearer <token>"
    private String newUserToken(String name, String role) throws Exception {
        String email = uniqueEmail(name);
        register(email, role);
        return "Bearer " + login(email).get("token").asText();
    }

    private JsonNode createTicket(String token, String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"description\":\"The VPN keeps disconnecting every hour\","
                + "\"priority\":\"High\",\"issueCategory\":\"Technical\"}";
        MvcResult result = mockMvc.perform(post("/api/ticket").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Open"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createAgent(String managerToken, String email) throws Exception {
        String body = "{\"name\":\"Meera Kapoor\",\"email\":\"" + email + "\",\"phone\":\"9820111223\","
                + "\"expertise\":\"Technical Support\",\"experience\":\"5 years\",\"status\":\"Available\","
                + "\"shiftTiming\":\"9 AM - 6 PM\",\"remarks\":\"Handles login issues\"}";
        MvcResult result = mockMvc.perform(post("/api/supportAgent").header("Authorization", managerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String withChanges(JsonNode ticket, String field, String jsonValue) throws Exception {
        String json = objectMapper.writeValueAsString(ticket);
        JsonNode copy = objectMapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).set(field, objectMapper.readTree(jsonValue));
        return objectMapper.writeValueAsString(copy);
    }

    // ---------------------------------------------------------------- auth

    @Test
    void registerHidesPasswordAndRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail("alice");
        JsonNode user = register(email, "Client");
        assertTrue(user.get("password") == null, "password must never be returned");

        String body = "{\"email\":\"" + email + "\",\"password\":\"Password@1\",\"username\":\"Alice\","
                + "\"mobileNumber\":\"9876543210\",\"userRole\":\"Client\"}";
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A user with this email already exists"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        String body = "{\"email\":\"bad\",\"password\":\"123\",\"username\":\"\",\"mobileNumber\":\"12\","
                + "\"userRole\":\"Admin\"}";
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsLoginDtoAndRejectsWrongPassword() throws Exception {
        String email = uniqueEmail("bob");
        register(email, "Manager");

        JsonNode loginDto = login(email);
        assertTrue(loginDto.get("token").asText().length() > 20);
        assertTrue("Manager".equals(loginDto.get("userRole").asText()));
        assertTrue(loginDto.get("userId").asLong() > 0);

        String body = "{\"email\":\"" + email + "\",\"password\":\"WrongPassword1\"}";
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ---------------------------------------------------------------- JWT + roles

    @Test
    void protectedUrlsNeedAValidToken() throws Exception {
        mockMvc.perform(get("/api/ticket")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ticket").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        assertTrue(errorLogRepo.count() > 0, "errors are recorded in the ErrorLogs table");
    }

    @Test
    void roleRulesFollowTheSrs() throws Exception {
        String manager = newUserToken("manager", "Manager");
        String client = newUserToken("client", "Client");
        String ticketBody = "{\"title\":\"x title\",\"description\":\"desc\",\"priority\":\"Low\","
                + "\"issueCategory\":\"General\"}";

        // Manager cannot create tickets; Client cannot list or add agents
        mockMvc.perform(post("/api/ticket").header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(ticketBody)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/supportAgent").header("Authorization", client)).andExpect(status().isForbidden());

        JsonNode ticket = createTicket(client, "Printer offline");
        long ticketId = ticket.get("ticketId").asLong();

        // GET /api/ticket/{id} is Client only (SRS)
        mockMvc.perform(get("/api/ticket/" + ticketId).header("Authorization", manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ticket/" + ticketId).header("Authorization", client)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ticket/999999").header("Authorization", client)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void clientCannotReadOrChangeAnotherClientsData() throws Exception {
        String alice = newUserToken("alice", "Client");
        String bob = newUserToken("bob", "Client");

        JsonNode ticket = createTicket(alice, "Laptop slow");
        long ticketId = ticket.get("ticketId").asLong();
        long aliceId = ticket.get("user").get("userId").asLong();

        mockMvc.perform(get("/api/ticket/" + ticketId).header("Authorization", bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ticket/user/" + aliceId).header("Authorization", bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ticket/" + ticketId).header("Authorization", bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feedback/user/" + aliceId).header("Authorization", bob))
                .andExpect(status().isForbidden());

        // GET /api/ticket for a client only returns their own tickets
        mockMvc.perform(get("/api/ticket").header("Authorization", bob)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ticket").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------------------------------------------------------- full lifecycle

    @Test
    void ticketLifecycleFromOpenToFeedback() throws Exception {
        String manager = newUserToken("manager", "Manager");
        String client = newUserToken("client", "Client");

        JsonNode agent = createAgent(manager, uniqueEmail("agent"));
        long agentId = agent.get("agentId").asLong();
        JsonNode ticket = createTicket(client, "Cannot log in");
        long ticketId = ticket.get("ticketId").asLong();

        // Duplicate agent email -> 409
        String duplicateAgent = objectMapper.writeValueAsString(agent).replace("\"agentId\":" + agentId, "\"agentId\":null");
        mockMvc.perform(post("/api/supportAgent").header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(duplicateAgent)).andExpect(status().isConflict());

        // Manager assigns the agent (status stays Open, SRS: "Agent Assigned")
        String assign = withChanges(ticket, "supportAgent", "{\"agentId\":" + agentId + "}");
        MvcResult assigned = mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(assign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportAgent.agentId").value(agentId))
                .andExpect(jsonPath("$.status").value("Open"))
                .andReturn();
        ticket = objectMapper.readTree(assigned.getResponse().getContentAsString());

        // Assigned ticket can no longer be deleted
        mockMvc.perform(delete("/api/ticket/" + ticketId).header("Authorization", client))
                .andExpect(status().isConflict());

        // Resolve without a summary -> 400 with the SRS message
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(withChanges(ticket, "status", "\"Resolved\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please provide resolution details before marking resolve."));

        // Client adds summary + satisfaction, then resolves
        String summary = withChanges(ticket, "resolutionSummary", "\"Password was reset\"");
        summary = withChanges(objectMapper.readTree(summary), "satisfied", "true");
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(summary)).andExpect(status().isOk());
        String resolve = withChanges(objectMapper.readTree(summary), "status", "\"Resolved\"");
        MvcResult resolved = mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(resolve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Resolved"))
                .andReturn();
        ticket = objectMapper.readTree(resolved.getResponse().getContentAsString());

        // Only the manager can close
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(withChanges(ticket, "status", "\"Closed\"")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(withChanges(ticket, "status", "\"Closed\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Closed"));

        // Client: tickets worked by the agent
        mockMvc.perform(get("/api/ticket/agent/" + agentId).header("Authorization", client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Feedback: created once, second attempt -> 409
        String feedback = "{\"feedbackText\":\"Quick and helpful\",\"category\":\"Service Quality\",\"rating\":5,"
                + "\"ticket\":{\"ticketId\":" + ticketId + "}}";
        mockMvc.perform(post("/api/feedback").header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supportAgent.agentId").value(agentId));
        mockMvc.perform(post("/api/feedback").header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/feedback").header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isForbidden());

        // Manager sees the feedback; the agent who worked tickets cannot be deleted
        mockMvc.perform(get("/api/feedback").header("Authorization", manager)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/supportAgent/" + agentId).header("Authorization", manager))
                .andExpect(status().isConflict());
    }

    @Test
    void openUnassignedTicketCanBeEditedAndDeletedByOwner() throws Exception {
        String client = newUserToken("client", "Client");
        JsonNode ticket = createTicket(client, "Email bounce");
        long ticketId = ticket.get("ticketId").asLong();

        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(withChanges(ticket, "priority", "\"Low\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("Low"));

        mockMvc.perform(delete("/api/ticket/" + ticketId).header("Authorization", client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/controller/SecurityIntegrationTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/service"
cat > "$APP/src/test/java/com/examly/springapp/service/ChatServiceTest.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/service/ChatServiceTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/service"
cat > "$APP/src/test/java/com/examly/springapp/service/ConversationMemoryTest.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    @Test
    void storesTurnsOldestFirst() {
        ConversationMemory memory = new ConversationMemory();
        memory.addTurn("s1", "q1", "a1");
        memory.addTurn("s1", "q2", "a2");

        List<ConversationMemory.Turn> history = memory.getHistory("s1");
        assertEquals(2, history.size());
        assertEquals("q1", history.get(0).getUserMessage());
        assertEquals("a2", history.get(1).getBotReply());
    }

    @Test
    void keepsOnlyTheLastEightTurns() {
        ConversationMemory memory = new ConversationMemory();
        for (int i = 1; i <= 10; i++) {
            memory.addTurn("s1", "q" + i, "a" + i);
        }
        List<ConversationMemory.Turn> history = memory.getHistory("s1");
        assertEquals(8, history.size());
        assertEquals("q3", history.get(0).getUserMessage());
        assertEquals("q10", history.get(7).getUserMessage());
    }

    @Test
    void sessionsAreSeparateAndCanBeCleared() {
        ConversationMemory memory = new ConversationMemory();
        memory.addTurn("s1", "q1", "a1");
        memory.addTurn("s2", "other", "answer");

        memory.clear("s1");
        assertTrue(memory.getHistory("s1").isEmpty());
        assertEquals(1, memory.getHistory("s2").size());
        assertTrue(memory.getHistory("unknown").isEmpty());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/service/ConversationMemoryTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/service"
cat > "$APP/src/test/java/com/examly/springapp/service/FaqServiceTest.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/service/FaqServiceTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/service"
cat > "$APP/src/test/java/com/examly/springapp/service/GeminiServiceTest.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/service/GeminiServiceTest.java"

mkdir -p "$APP/src/test/java/com/examly/springapp/service/impl"
cat > "$APP/src/test/java/com/examly/springapp/service/impl/TicketServiceImplTest.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepo ticketRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private SupportAgentRepo supportAgentRepo;
    @Mock
    private FeedbackRepo feedbackRepo;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private User client;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setUserId(1L);
        client.setUserRole("Client");
    }

    private Ticket ticket(String title) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription("Something is broken and needs a fix");
        ticket.setPriority("High");
        ticket.setIssueCategory("Technical");
        ticket.setUser(client);
        return ticket;
    }

    private SupportAgent agent(Long id, String status) {
        SupportAgent agent = new SupportAgent();
        agent.setAgentId(id);
        agent.setStatus(status);
        return agent;
    }

    @Test
    void newTicketStartsOpenWithoutAgent() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        when(ticketRepo.findByUserUserId(1L)).thenReturn(new ArrayList<>());
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(call -> call.getArgument(0));

        Ticket request = ticket("VPN down");
        request.setStatus("Closed");
        request.setSupportAgent(agent(5L, "Available"));
        Ticket saved = ticketService.addTicket(request);

        assertEquals("Open", saved.getStatus());
        assertNull(saved.getSupportAgent());
        assertNotNull(saved.getCreatedDate());
    }

    @Test
    void duplicateTitleThrowsDuplicateTicketException() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findByUserUserId(1L)).thenReturn(List.of(existing));

        assertThrows(DuplicateTicketException.class, () -> ticketService.addTicket(ticket("vpn DOWN")));
    }

    @Test
    void resolvingWithoutSummaryIsRejected() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        Ticket request = ticket("VPN down");
        request.setStatus("Resolved");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ticketService.updateTicket(10L, request));
        assertEquals("Please provide resolution details before marking resolve.", ex.getMessage());
    }

    @Test
    void resolvingWithSummarySetsResolutionDate() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(call -> call.getArgument(0));

        Ticket request = ticket("VPN down");
        request.setStatus("Resolved");
        request.setResolutionSummary("Restarted the gateway");
        request.setSatisfied(true);
        Ticket saved = ticketService.updateTicket(10L, request);

        assertEquals("Resolved", saved.getStatus());
        assertNotNull(saved.getResolutionDate());
        assertEquals(Boolean.TRUE, saved.getSatisfied());
    }

    @Test
    void onlyResolvedTicketCanBeClosed() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        Ticket request = ticket("VPN down");
        request.setStatus("Closed");

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateTicket(10L, request));
    }

    @Test
    void unavailableAgentCannotBeAssigned() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(supportAgentRepo.findById(5L)).thenReturn(Optional.of(agent(5L, "Unavailable")));

        Ticket request = ticket("VPN down");
        request.setSupportAgent(agent(5L, null));

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateTicket(10L, request));
    }

    @Test
    void assignedTicketCannotBeDeleted() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        assertThrows(TicketDeletionException.class, () -> ticketService.deleteTicket(10L));
        verify(ticketRepo, never()).delete(any(Ticket.class));
    }
}
SUPPORTSPHERE_EOF
echo "  wrote src/test/java/com/examly/springapp/service/impl/TicketServiceImplTest.java"

mkdir -p "$APP/src/test/resources"
cat > "$APP/src/test/resources/application.properties" <<'SUPPORTSPHERE_EOF'
# Test profile: in-memory H2 in MySQL mode, so tests run without a MySQL server.
spring.datasource.url=jdbc:h2:mem:appdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,VALUE
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false

jwt.secret=VGVzdE9ubHlTZWNyZXRGb3JTdXBwb3J0U3BoZXJlSnVuaXRUZXN0czAxMjM0NTY3ODk=
jwt.expiration=3600000
app.cors.allowed-origins=http://localhost:8081
app.security.public-read-endpoints=false

logging.level.com.examly.springapp=INFO

# Chatbot tests never call Gemini: no API key -> lexical fallback
gemini.api.key=
gemini.embedding.model=gemini-embedding-2
gemini.generation.model=gemini-2.5-flash
chatbot.similarity.threshold=0.65
chatbot.lexical.threshold=0.08
SUPPORTSPHERE_EOF
echo "  wrote src/test/resources/application.properties"

echo ""
echo "Done: 63 files written."
echo "Next:  cd \"$APP\" && mvn clean test     (then: mvn spring-boot:run)"
echo "Optional for the AI chatbot:  export GEMINI_API_KEY=<your key>"
