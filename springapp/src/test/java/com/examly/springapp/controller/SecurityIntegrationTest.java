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
