package com.examly.springapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Guarantees of the backend refactor: enums keep the same JSON and database values,
// error messages and status codes are unchanged, and request ids / logs behave as intended.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BackendRefactorTest {

    private static final String PASSWORD = "Password@1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- helpers

    private String uniqueEmail(String name) {
        return name + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String register(String email, String role) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"username\":\"Test User\","
                + "\"mobileNumber\":\"9876543210\",\"userRole\":\"" + role + "\"}";
        MvcResult result = mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userRole").value(role))
                .andReturn();
        return json(result).get("userId").asText();
    }

    private String token(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + json(result).get("token").asText();
    }

    private String newUser(String name, String role) throws Exception {
        String email = uniqueEmail(name);
        register(email, role);
        return token(email);
    }

    private JsonNode createTicket(String client, String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"description\":\"Something is broken today\","
                + "\"priority\":\"High\",\"issueCategory\":\"Technical\"}";
        return json(mockMvc.perform(post("/api/ticket").header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Open"))
                .andExpect(jsonPath("$.priority").value("High"))
                .andReturn());
    }

    private JsonNode createAgent(String manager, String status) throws Exception {
        String body = "{\"name\":\"Arjun Mehta\",\"email\":\"" + uniqueEmail("agent") + "\",\"phone\":\"9930244556\","
                + "\"expertise\":\"Networking\",\"experience\":\"3 years\",\"status\":\"" + status + "\","
                + "\"shiftTiming\":\"12 PM - 9 PM\",\"remarks\":\"VPN tickets\"}";
        return json(mockMvc.perform(post("/api/supportAgent").header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(status))
                .andReturn());
    }

    private String with(JsonNode node, String field, String jsonValue) throws Exception {
        ObjectNode copy = (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(node));
        copy.set(field, objectMapper.readTree(jsonValue));
        return objectMapper.writeValueAsString(copy);
    }

    // ---------------------------------------------------------------- enums: same database + JSON values

    @Test
    void enumsAreStoredWithTheSameLabelsAsBefore() throws Exception {
        String manager = newUser("manager", "Manager");
        String client = newUser("client", "Client");
        JsonNode agent = createAgent(manager, "Available");
        JsonNode ticket = createTicket(client, "Database labels");
        long ticketId = ticket.get("ticketId").asLong();
        long userId = ticket.get("user").get("userId").asLong();

        // Manager moves the ticket to In Progress (allowed by the existing rules)
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(with(ticket, "status", "\"In Progress\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("In Progress"));

        assertEquals("In Progress", jdbcTemplate.queryForObject(
                "select status from ticket where ticket_id = ?", String.class, ticketId));
        assertEquals("High", jdbcTemplate.queryForObject(
                "select priority from ticket where ticket_id = ?", String.class, ticketId));
        assertEquals("Available", jdbcTemplate.queryForObject(
                "select status from support_agent where agent_id = ?", String.class, agent.get("agentId").asLong()));
        assertEquals("Client", jdbcTemplate.queryForObject(
                "select user_role from users where user_id = ?", String.class, userId));
    }

    @Test
    void rowsWrittenWithTheOldStringValuesStillLoad() throws Exception {
        String client = newUser("legacy", "Client");
        JsonNode ticket = createTicket(client, "Legacy row");
        long ticketId = ticket.get("ticketId").asLong();

        // Simulate a row saved by the previous version of the application
        jdbcTemplate.update("update ticket set status = 'In Progress', priority = 'Low' where ticket_id = ?", ticketId);

        mockMvc.perform(get("/api/ticket/" + ticketId).header("Authorization", client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("In Progress"))
                .andExpect(jsonPath("$.priority").value("Low"));
    }

    @Test
    void invalidEnumValuesKeepTheirValidationMessages() throws Exception {
        String client = newUser("client", "Client");
        JsonNode ticket = createTicket(client, "Bad status");

        mockMvc.perform(put("/api/ticket/" + ticket.get("ticketId").asLong()).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(with(ticket, "status", "\"Pending\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status must be Open, In Progress, Resolved or Closed"));

        String badRole = "{\"email\":\"" + uniqueEmail("x") + "\",\"password\":\"" + PASSWORD + "\",\"username\":\"X Y\","
                + "\"mobileNumber\":\"9876543210\",\"userRole\":\"Admin\"}";
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(badRole))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User role must be Manager or Client"));

        String noPriority = "{\"title\":\"No priority\",\"description\":\"desc\",\"issueCategory\":\"General\"}";
        mockMvc.perform(post("/api/ticket").header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON).content(noPriority))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").value("Priority is required"));
    }

    // ---------------------------------------------------------------- repository queries

    @Test
    void duplicateTitleIsCaseInsensitivePerClient() throws Exception {
        String alice = newUser("alice", "Client");
        String bob = newUser("bob", "Client");
        createTicket(alice, "Printer Jam");

        String sameTitle = "{\"title\":\"printer jam\",\"description\":\"desc text\",\"priority\":\"Low\","
                + "\"issueCategory\":\"General\"}";
        mockMvc.perform(post("/api/ticket").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(sameTitle))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A ticket with this title already exists"));

        // Another client may use the same title
        createTicket(bob, "Printer Jam");
    }

    @Test
    void ticketsWorkedOnlyReturnsTheClientsOwnTickets() throws Exception {
        String manager = newUser("manager", "Manager");
        String alice = newUser("alice", "Client");
        String bob = newUser("bob", "Client");
        long agentId = createAgent(manager, "Available").get("agentId").asLong();

        for (String client : new String[] { alice, bob }) {
            JsonNode ticket = createTicket(client, "Worked ticket");
            mockMvc.perform(put("/api/ticket/" + ticket.get("ticketId").asLong()).header("Authorization", manager)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(with(ticket, "supportAgent", "{\"agentId\":" + agentId + "}")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/ticket/agent/" + agentId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/ticket/agent/999999").header("Authorization", alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void unavailableAgentCannotBeAssigned() throws Exception {
        String manager = newUser("manager", "Manager");
        String client = newUser("client", "Client");
        long agentId = createAgent(manager, "Unavailable").get("agentId").asLong();
        JsonNode ticket = createTicket(client, "Needs an agent");

        mockMvc.perform(put("/api/ticket/" + ticket.get("ticketId").asLong()).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(with(ticket, "supportAgent", "{\"agentId\":" + agentId + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This support agent is currently unavailable"));
    }

    // ---------------------------------------------------------------- 404 bodies

    @Test
    void notFoundReturnsTheStandardErrorBody() throws Exception {
        String manager = newUser("manager", "Manager");
        String client = newUser("client", "Client");

        mockMvc.perform(get("/api/ticket/999999").header("Authorization", client))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Ticket not found with id 999999"));
        mockMvc.perform(get("/api/supportAgent/999999").header("Authorization", manager))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Support agent not found with id 999999"));
    }

    // ---------------------------------------------------------------- request id + logging

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        MvcResult generated = mockMvc.perform(get("/api/faqs"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        assertFalse(generated.getResponse().getHeader("X-Request-Id").isBlank());

        mockMvc.perform(get("/api/faqs").header("X-Request-Id", "trace-123"))
                .andExpect(header().string("X-Request-Id", "trace-123"));

        // An unsafe id (could inject text into the logs) is replaced by a generated one
        MvcResult replaced = mockMvc.perform(get("/api/faqs").header("X-Request-Id", "bad id\nINFO fake log line"))
                .andReturn();
        assertNotEquals("bad id\nINFO fake log line", replaced.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void passwordsAndTokensAreNeverLogged(CapturedOutput output) throws Exception {
        String email = uniqueEmail("secret");
        register(email, "Client");
        String bearer = token(email);
        mockMvc.perform(get("/api/ticket").header("Authorization", bearer)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"WrongPassword9!\"}"))
                .andExpect(status().isUnauthorized());

        String logs = output.getAll();
        assertTrue(logs.contains("Login successful"), "business events are logged");
        assertTrue(logs.contains("requestId="), "log lines carry the request id");
        assertFalse(logs.contains(PASSWORD), "password must not be logged");
        assertFalse(logs.contains("WrongPassword9!"), "rejected password must not be logged");
        assertFalse(logs.contains(bearer.substring(7)), "JWT must not be logged");
        assertFalse(logs.contains("$2a$"), "BCrypt hash must not be logged");
    }
}
