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
