package com.examly.springapp.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.repository.ErrorLogRepo;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end checks of the security chain against an in-memory database:
 * DAO login, JWT filter, entry point (401), role rules (403), ownership (403) and status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private TicketRepo ticketRepo;
    @Autowired
    private FeedbackRepo feedbackRepo;
    @Autowired
    private SupportAgentRepo supportAgentRepo;
    @Autowired
    private ErrorLogRepo errorLogRepo;

    @BeforeEach
    void cleanDatabase() {
        feedbackRepo.deleteAll();
        ticketRepo.deleteAll();
        supportAgentRepo.deleteAll();
        userRepo.deleteAll();
        errorLogRepo.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    private void register(String email, String role) throws Exception {
        String body = """
                {"email":"%s","password":"Password@1","username":"%s","mobileNumber":"9876543210","userRole":"%s"}
                """.formatted(email, email.substring(0, email.indexOf('@')), role);
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Password@1\"}";
        MvcResult result = mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(JsonNode login) {
        return "Bearer " + login.get("token").asText();
    }

    private long createTicket(String auth, String title) throws Exception {
        String body = """
                {"title":"%s","description":"Something is broken","priority":"High","issueCategory":"Technical"}
                """.formatted(title);
        MvcResult result = mockMvc.perform(post("/api/ticket").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Open"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("ticketId").asLong();
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void registerReturns201AndDuplicateEmailReturns409() throws Exception {
        register("client@test.com", "Client");
        String body = """
                {"email":"client@test.com","password":"Password@1","username":"other","mobileNumber":"9876543210","userRole":"Client"}
                """;
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A user with this email already exists"));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        register("hash@test.com", "Client");
        String stored = userRepo.findByEmail("hash@test.com").orElseThrow().getPassword();
        org.assertj.core.api.Assertions.assertThat(stored).isNotEqualTo("Password@1").startsWith("$2");
    }

    @Test
    void invalidRegistrationReturns400() throws Exception {
        String body = "{\"email\":\"bad\",\"password\":\"x\",\"username\":\"\",\"mobileNumber\":\"12\",\"userRole\":\"Admin\"}";
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()));
    }

    @Test
    void loginReturnsLoginDtoAndWrongPasswordReturns401() throws Exception {
        register("manager@test.com", "Manager");
        JsonNode login = login("manager@test.com");
        org.assertj.core.api.Assertions.assertThat(login.get("token").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(login.get("userRole").asText()).isEqualTo("Manager");
        org.assertj.core.api.Assertions.assertThat(login.get("userId").asLong()).isPositive();
        org.assertj.core.api.Assertions.assertThat(login.get("username").asText()).isEqualTo("manager");

        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"manager@test.com\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ JWT filter / entry point

    @Test
    void missingInvalidAndExpiredTokensReturn401() throws Exception {
        register("client@test.com", "Client");
        mockMvc.perform(get("/api/ticket")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ticket").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        UserPrinciple principle = UserPrinciple.build(userRepo.findByEmail("client@test.com").orElseThrow());
        Date past = new Date(System.currentTimeMillis() - 60_000);
        String expired = jwtUtils.generateTokenWithExpiry(principle, new Date(past.getTime() - 60_000), past);
        mockMvc.perform(get("/api/ticket").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("JWT token has expired. Please login again."));
    }

    // ------------------------------------------------------------------ role authorization

    @Test
    void wrongRoleReturns403() throws Exception {
        register("manager@test.com", "Manager");
        register("client@test.com", "Client");
        String manager = bearer(login("manager@test.com"));
        String client = bearer(login("client@test.com"));

        // Manager cannot create tickets or feedback
        mockMvc.perform(post("/api/ticket").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"description\":\"d\",\"priority\":\"Low\",\"issueCategory\":\"General\"}"))
                .andExpect(status().isForbidden());
        // Client cannot manage agents
        mockMvc.perform(get("/api/supportAgent").header("Authorization", client)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/supportAgent/1").header("Authorization", client))
                .andExpect(status().isForbidden());
        // Manager on an empty agent list gets 204
        mockMvc.perform(get("/api/supportAgent").header("Authorization", manager)).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ resource ownership

    @Test
    void clientCannotReadAnotherClientsResources() throws Exception {
        register("alice@test.com", "Client");
        register("bob@test.com", "Client");
        JsonNode alice = login("alice@test.com");
        JsonNode bob = login("bob@test.com");

        long aliceTicket = createTicket(bearer(alice), "Alice printer");

        mockMvc.perform(get("/api/ticket/user/" + alice.get("userId").asLong()).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ticket/user/" + alice.get("userId").asLong()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ticket/" + aliceTicket).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/ticket/" + aliceTicket).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feedback/user/" + alice.get("userId").asLong()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ ticket lifecycle

    @Test
    void ticketLifecycleEnforcesBusinessRules() throws Exception {
        register("manager@test.com", "Manager");
        register("client@test.com", "Client");
        String manager = bearer(login("manager@test.com"));
        String client = bearer(login("client@test.com"));

        long ticketId = createTicket(client, "VPN down");

        // duplicate title for the same client -> 409
        mockMvc.perform(post("/api/ticket").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"vpn down\",\"description\":\"again\",\"priority\":\"Low\",\"issueCategory\":\"Technical\"}"))
                .andExpect(status().isConflict());

        // missing resource -> 404
        mockMvc.perform(get("/api/ticket/99999").header("Authorization", client)).andExpect(status().isNotFound());

        // Resolved without a summary -> 400
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"VPN down\",\"description\":\"Something is broken\",\"priority\":\"High\","
                        + "\"issueCategory\":\"Technical\",\"status\":\"Resolved\"}"))
                .andExpect(status().isBadRequest());

        // Resolved with a summary -> 200
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"VPN down\",\"description\":\"Something is broken\",\"priority\":\"High\","
                        + "\"issueCategory\":\"Technical\",\"status\":\"Resolved\",\"resolutionSummary\":\"Fixed\","
                        + "\"satisfied\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Resolved"))
                .andExpect(jsonPath("$.resolutionDate", notNullValue()));

        // Manager closes it -> 200
        mockMvc.perform(put("/api/ticket/" + ticketId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"VPN down\",\"description\":\"Something is broken\",\"priority\":\"High\","
                        + "\"issueCategory\":\"Technical\",\"status\":\"Closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Closed"));

        // Feedback on the closed ticket -> 201, duplicate -> 409, invalid rating -> 400
        String feedback = "{\"feedbackText\":\"Great\",\"ticketId\":" + ticketId
                + ",\"category\":\"Service Quality\",\"rating\":5}";
        mockMvc.perform(post("/api/feedback").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content(feedback)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/feedback").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content(feedback)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/feedback").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedbackText\":\"x\",\"ticketId\":" + ticketId + ",\"category\":\"c\",\"rating\":9}"))
                .andExpect(status().isBadRequest());

        // Manager can read all feedback but cannot post
        mockMvc.perform(get("/api/feedback").header("Authorization", manager)).andExpect(status().isOk());
        mockMvc.perform(post("/api/feedback").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content(feedback)).andExpect(status().isForbidden());
    }

    @Test
    void handledErrorsArePersistedToErrorLogs() throws Exception {
        mockMvc.perform(get("/api/ticket")).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(errorLogRepo.count()).isPositive();
    }
}
