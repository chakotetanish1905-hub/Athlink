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
