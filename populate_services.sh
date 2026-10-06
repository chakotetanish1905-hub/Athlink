#!/usr/bin/env bash
# =====================================================================================
#  SupportSphere - writes ONLY the Spring Boot service layer:
#     service/       UserService, TicketService, SupportAgentService, FeedbackService,
#                    GeminiService, FaqService, ConversationMemory, ChatService (Phase 2)
#     service/impl/  UserServiceImpl, TicketServiceImpl, SupportAgentServiceImpl, FeedbackServiceImpl
#
#  Usage (from the folder that contains "springapp", from inside springapp, or pass the path):
#      bash populate_services.sh
#      bash populate_services.sh /path/to/project
#
#  Folders are created only if they don't exist (mkdir -p); existing files are overwritten.
#  The old service implementations that used to sit directly in service/ are removed, because
#
#  NOTE: these services use the enums in model/ (TicketStatus, TicketPriority, AgentStatus, UserRole)
#  and the existsBy... repository methods from the backend refactor, so the rest of the backend
#  must be on the same version for them to compile.
#  they now live in service/impl and two classes with the same name would not compile.
# =====================================================================================
set -e

TARGET="${1:-.}"
if [ -d "$TARGET/springapp" ]; then
  APP="$TARGET/springapp"
else
  APP="$TARGET"
fi
APP="$(mkdir -p "$APP" && cd "$APP" && pwd)"

SERVICE="$APP/src/main/java/com/examly/springapp/service"
mkdir -p "$SERVICE/impl"
echo "Writing service layer into: $SERVICE"

# Old implementations from the previous version (they now live in service/impl)
rm -f "$SERVICE/UserServiceImpl.java" "$SERVICE/TicketServiceImpl.java" \
      "$SERVICE/SupportAgentServiceImpl.java" "$SERVICE/FeedbackServiceImpl.java"

# =====================================================================================
# service/
# =====================================================================================

cat > "$SERVICE/ChatService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);

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

        // The question itself is not logged: it is user-written text and may contain personal data
        LOGGER.debug("Chat answered: sessionId={} matched={} faqId={} source={} score={}", sessionId,
                match.isMatched(), matchedFaqId, match.isSemantic() ? "semantic" : "lexical", round(match.getScore()));

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
echo "  wrote service/ChatService.java"

cat > "$SERVICE/ConversationMemory.java" <<'SUPPORTSPHERE_EOF'
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
echo "  wrote service/ConversationMemory.java"

cat > "$SERVICE/FaqService.java" <<'SUPPORTSPHERE_EOF'
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
SUPPORTSPHERE_EOF
echo "  wrote service/FaqService.java"

cat > "$SERVICE/FeedbackService.java" <<'SUPPORTSPHERE_EOF'
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
echo "  wrote service/FeedbackService.java"

cat > "$SERVICE/GeminiService.java" <<'SUPPORTSPHERE_EOF'
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
echo "  wrote service/GeminiService.java"

cat > "$SERVICE/SupportAgentService.java" <<'SUPPORTSPHERE_EOF'
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
echo "  wrote service/SupportAgentService.java"

cat > "$SERVICE/TicketService.java" <<'SUPPORTSPHERE_EOF'
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

    // Only the given client's tickets that were handled by the agent ("Tickets Worked")
    List<Ticket> getTicketsByAgentIdForUser(Long agentId, Long userId);

    List<Ticket> getTicketsByUserId(Long userId);
}
SUPPORTSPHERE_EOF
echo "  wrote service/TicketService.java"

cat > "$SERVICE/UserService.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service;

import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;

public interface UserService {

    User createUser(User user);

    LoginDTO loginUser(User user);
}
SUPPORTSPHERE_EOF
echo "  wrote service/UserService.java"

# =====================================================================================
# service/impl/
# =====================================================================================

cat > "$SERVICE/impl/FeedbackServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.FeedbackService;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private final FeedbackRepo feedbackRepo;
    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;

    public FeedbackServiceImpl(FeedbackRepo feedbackRepo, TicketRepo ticketRepo, UserRepo userRepo) {
        this.feedbackRepo = feedbackRepo;
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public Feedback createFeedback(Feedback feedback) {
        if (feedback.getTicket() == null || feedback.getTicket().getTicketId() == null) {
            throw new IllegalArgumentException("Please select the ticket you are reviewing");
        }

        Ticket ticket = ticketRepo.findById(feedback.getTicket().getTicketId())
                .orElseThrow(() -> new NoSuchElementException("Ticket not found"));
        User user = userRepo.findById(feedback.getUser().getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // A client can only review their own ticket
        if (!ticket.getUser().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("You can only give feedback on your own tickets");
        }
        // Feedback is allowed only after the ticket is Resolved or Closed
        if (!ticket.getStatus().isResolvedOrClosed()) {
            throw new IllegalArgumentException("Feedback can be given only for a Resolved or Closed ticket");
        }
        // One feedback per ticket
        if (feedbackRepo.existsByTicketTicketId(ticket.getTicketId())) {
            throw new IllegalStateException("You have already given feedback for this ticket");
        }

        feedback.setFeedbackId(null);
        feedback.setUser(user);
        feedback.setTicket(ticket);
        feedback.setSupportAgent(ticket.getSupportAgent());
        feedback.setDate(LocalDate.now());
        Feedback saved = feedbackRepo.save(feedback);
        LOGGER.info("Feedback created: feedbackId={} ticketId={} userId={} rating={}",
                saved.getFeedbackId(), ticket.getTicketId(), user.getUserId(), saved.getRating());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Feedback getFeedbackById(Long feedbackId) {
        return feedbackRepo.findById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Feedback not found with id " + feedbackId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepo.findAll();
    }

    @Override
    @Transactional
    public Feedback deleteFeedback(Long feedbackId) {
        Feedback feedback = getFeedbackById(feedbackId);
        feedbackRepo.delete(feedback);
        LOGGER.info("Feedback deleted: feedbackId={}", feedbackId);
        return feedback;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Feedback> getFeedbacksByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return feedbackRepo.findByUserUserId(userId);
    }
}
SUPPORTSPHERE_EOF
echo "  wrote service/impl/FeedbackServiceImpl.java"

cat > "$SERVICE/impl/SupportAgentServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.AgentDeletionException;
import com.examly.springapp.exceptions.DuplicateAgentException;
import com.examly.springapp.model.AgentStatus;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.service.SupportAgentService;

@Service
public class SupportAgentServiceImpl implements SupportAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportAgentServiceImpl.class);

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
    @Transactional
    public SupportAgent addSupportAgent(SupportAgent supportAgent) {
        String email = supportAgent.getEmail().trim().toLowerCase();

        // Two agents cannot share the same email
        if (supportAgentRepo.existsByEmail(email)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        supportAgent.setAgentId(null);
        supportAgent.setEmail(email);
        supportAgent.setAddedDate(LocalDate.now());
        SupportAgent saved = supportAgentRepo.save(supportAgent);
        LOGGER.info("Support agent added: agentId={} expertise={} status={}",
                saved.getAgentId(), saved.getExpertise(), saved.getStatus().getLabel());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportAgent> getSupportAgentById(Long agentId) {
        return supportAgentRepo.findById(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportAgent> getAllSupportAgents() {
        return supportAgentRepo.findAll();
    }

    @Override
    @Transactional
    public SupportAgent updateSupportAgent(Long agentId, SupportAgent supportAgent) {
        SupportAgent existing = supportAgentRepo.findById(agentId)
                .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + agentId));

        String email = supportAgent.getEmail().trim().toLowerCase();
        if (supportAgentRepo.existsByEmailAndAgentIdNot(email, agentId)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        AgentStatus oldStatus = existing.getStatus();
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
        SupportAgent saved = supportAgentRepo.save(existing);
        LOGGER.info("Support agent updated: agentId={}", agentId);
        if (oldStatus != saved.getStatus()) {
            LOGGER.info("Support agent availability changed: agentId={} {} -> {}",
                    agentId, oldStatus.getLabel(), saved.getStatus().getLabel());
        }
        return saved;
    }

    @Override
    @Transactional
    public SupportAgent deleteSupportAgent(Long agentId) {
        SupportAgent agent = supportAgentRepo.findById(agentId)
                .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + agentId));

        // An agent that worked on tickets (or received feedback) is kept for the ticket history
        if (ticketRepo.existsBySupportAgentAgentId(agentId) || feedbackRepo.existsBySupportAgentAgentId(agentId)) {
            throw new AgentDeletionException(
                    "This agent is assigned to tickets and cannot be deleted. Mark the agent Unavailable instead.");
        }

        supportAgentRepo.delete(agent);
        LOGGER.info("Support agent deleted: agentId={}", agentId);
        return agent;
    }
}
SUPPORTSPHERE_EOF
echo "  wrote service/impl/SupportAgentServiceImpl.java"

cat > "$SERVICE/impl/TicketServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.AgentStatus;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.TicketStatus;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.TicketService;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketServiceImpl.class);

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
    @Transactional
    public Ticket addTicket(Ticket ticket) {
        Long userId = ticket.getUser().getUserId();
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        String title = ticket.getTitle().trim();
        if (ticketRepo.existsByUserUserIdAndTitleIgnoreCase(userId, title)) {
            throw new DuplicateTicketException("A ticket with this title already exists");
        }

        // Every new ticket starts as Open, without an agent and without a resolution
        ticket.setTicketId(null);
        ticket.setTitle(title);
        ticket.setUser(user);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedDate(LocalDate.now());
        ticket.setResolutionDate(null);
        ticket.setResolutionSummary(null);
        ticket.setSatisfied(null);
        ticket.setSupportAgent(null);

        Ticket saved = ticketRepo.save(ticket);
        LOGGER.info("Ticket created: ticketId={} userId={} priority={} category={}",
                saved.getTicketId(), userId, saved.getPriority().getLabel(), saved.getIssueCategory());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> getTicketById(Long ticketId) {
        return ticketRepo.findById(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return ticketRepo.findAll();
    }

    @Override
    @Transactional
    public Ticket updateTicket(Long ticketId, Ticket ticket) {
        Ticket existing = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + ticketId));

        // 1. Ticket details can only change while the ticket is Open and no agent is assigned
        boolean detailsChanged = !existing.getTitle().equals(ticket.getTitle().trim())
                || !existing.getDescription().equals(ticket.getDescription())
                || existing.getPriority() != ticket.getPriority()
                || !existing.getIssueCategory().equals(ticket.getIssueCategory());
        if (detailsChanged) {
            if (existing.getStatus() != TicketStatus.OPEN || existing.getSupportAgent() != null) {
                throw new IllegalArgumentException(
                        "A ticket can only be edited while it is Open and no agent is assigned");
            }
            String title = ticket.getTitle().trim();
            if (ticketRepo.existsByUserUserIdAndTitleIgnoreCaseAndTicketIdNot(
                    existing.getUser().getUserId(), title, ticketId)) {
                throw new DuplicateTicketException("A ticket with this title already exists");
            }
            existing.setTitle(title);
            existing.setDescription(ticket.getDescription());
            existing.setPriority(ticket.getPriority());
            existing.setIssueCategory(ticket.getIssueCategory());
            LOGGER.info("Ticket details updated: ticketId={}", ticketId);
        }

        // 2. Assign a support agent (only an Available agent can be assigned)
        if (ticket.getSupportAgent() != null && ticket.getSupportAgent().getAgentId() != null) {
            Long newAgentId = ticket.getSupportAgent().getAgentId();
            SupportAgent currentAgent = existing.getSupportAgent();
            if (currentAgent == null || !currentAgent.getAgentId().equals(newAgentId)) {
                SupportAgent agent = supportAgentRepo.findById(newAgentId)
                        .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + newAgentId));
                if (agent.getStatus() != AgentStatus.AVAILABLE) {
                    throw new IllegalArgumentException("This support agent is currently unavailable");
                }
                existing.setSupportAgent(agent);
                LOGGER.info("Agent assigned: ticketId={} agentId={}", ticketId, newAgentId);
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
        TicketStatus newStatus = ticket.getStatus();
        if (newStatus != null && newStatus != existing.getStatus()) {
            TicketStatus oldStatus = existing.getStatus();
            validateStatusTransition(existing, newStatus);
            if (newStatus == TicketStatus.RESOLVED) {
                existing.setResolutionDate(LocalDate.now());
            }
            existing.setStatus(newStatus);
            LOGGER.info("Ticket status changed: ticketId={} {} -> {}", ticketId, oldStatus.getLabel(), newStatus.getLabel());
        }

        return ticketRepo.save(existing);
    }

    @Override
    @Transactional
    public Ticket deleteTicket(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + ticketId));

        if (ticket.getStatus() != TicketStatus.OPEN || ticket.getSupportAgent() != null) {
            throw new TicketDeletionException("Only an Open ticket without an assigned agent can be deleted");
        }
        if (feedbackRepo.existsByTicketTicketId(ticketId)) {
            throw new TicketDeletionException("This ticket has feedback and cannot be deleted");
        }
        ticketRepo.delete(ticket);
        LOGGER.info("Ticket deleted: ticketId={}", ticketId);
        return ticket;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByAgentId(Long agentId) {
        checkAgentExists(agentId);
        return ticketRepo.findBySupportAgentAgentId(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByAgentIdForUser(Long agentId, Long userId) {
        checkAgentExists(agentId);
        return ticketRepo.findBySupportAgentAgentIdAndUserUserId(agentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return ticketRepo.findByUserUserId(userId);
    }

    // All status rules in one place. Ticket lifecycle:
    //   Open <-> In Progress  ->  Resolved (by the client, needs a summary and an agent)  ->  Closed (only from Resolved)
    // A Resolved or Closed ticket can never go back to Open or In Progress.
    private void validateStatusTransition(Ticket ticket, TicketStatus newStatus) {
        TicketStatus currentStatus = ticket.getStatus();

        if (currentStatus.isResolvedOrClosed()
                && !(currentStatus == TicketStatus.RESOLVED && newStatus == TicketStatus.CLOSED)) {
            throw new IllegalArgumentException("A resolved or closed ticket cannot be reopened");
        }

        if (newStatus == TicketStatus.RESOLVED) {
            if (ticket.getResolutionSummary() == null || ticket.getResolutionSummary().isBlank()) {
                throw new IllegalArgumentException("Please provide resolution details before marking resolve.");
            }
            if (ticket.getSupportAgent() == null) {
                throw new IllegalArgumentException("A support agent must be assigned before resolving the ticket");
            }
        }

        if (newStatus == TicketStatus.CLOSED && currentStatus != TicketStatus.RESOLVED) {
            throw new IllegalArgumentException("Only a resolved ticket can be closed");
        }
    }

    private void checkAgentExists(Long agentId) {
        if (!supportAgentRepo.existsById(agentId)) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }
    }
}
SUPPORTSPHERE_EOF
echo "  wrote service/impl/TicketServiceImpl.java"

cat > "$SERVICE/impl/UserServiceImpl.java" <<'SUPPORTSPHERE_EOF'
package com.examly.springapp.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

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
    @Transactional
    public User createUser(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // Check whether the email already exists (answered with 409 by GlobalExceptionHandler)
        if (userRepo.existsByEmail(email)) {
            throw new IllegalStateException("A user with this email already exists");
        }

        user.setUserId(null);
        user.setEmail(email);
        user.setUsername(user.getUsername().trim());
        // Never store the plain password: save the BCrypt hash instead
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepo.save(user);
        LOGGER.info("User registered: userId={} role={}", saved.getUserId(), saved.getUserRole().getLabel());
        return saved;
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
        LOGGER.info("Login successful: userId={} role={}", principle.getUserId(), principle.getUserRole());

        return new LoginDTO(token, principle.getDisplayName(), principle.getUserRole(), principle.getUserId());
    }
}
SUPPORTSPHERE_EOF
echo "  wrote service/impl/UserServiceImpl.java"

echo "Done: 12 service files written."
