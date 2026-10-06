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
