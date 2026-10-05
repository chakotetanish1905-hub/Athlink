package com.examly.springapp.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.examly.springapp.config.SwaggerConfig;
import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.model.TicketRequestDTO;
import com.examly.springapp.model.TicketResponseDTO;
import com.examly.springapp.service.TicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ticket")
@Tag(name = "Tickets", description = "Support ticket management")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class TicketController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @Operation(summary = "Add ticket (Client)",
            description = "Creates a ticket for the authenticated client. Status always starts as Open.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "title, description, priority (High|Medium|Low), issueCategory")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ticket created",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Manager / other user's id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "A ticket with this title already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<TicketResponseDTO> addTicket(@Valid @RequestBody TicketRequestDTO request) {
        LOGGER.debug("POST /api/ticket");
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addTicket(request));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "View ticket by id (Client)", description = "Returns one of the client's own tickets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket found",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Manager or for another client's ticket",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<TicketResponseDTO> getTicketById(
            @Parameter(description = "Ticket id", required = true) @PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));
    }

    @GetMapping
    @Operation(summary = "View all tickets (Manager, Client)",
            description = "Manager receives every ticket; a Client receives only their own tickets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tickets found",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketResponseDTO.class)))),
            @ApiResponse(responseCode = "204", description = "No tickets") })
    public ResponseEntity<List<TicketResponseDTO>> getAllTickets() {
        List<TicketResponseDTO> tickets = ticketService.getAllTickets();
        if (tickets.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(tickets);
    }

    @PutMapping("/{ticketId}")
    @Operation(summary = "Update ticket (Manager, Client)",
            description = "Client: edit own Open ticket, or resolve it with resolutionSummary + satisfied. "
                    + "Manager: assign agentId, change status, close a Resolved ticket. "
                    + "Status Resolved requires a resolutionSummary.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Full ticket payload")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket updated",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Business rule violated (e.g. Resolved without summary)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ticket or agent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate title",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<TicketResponseDTO> updateTicket(
            @Parameter(description = "Ticket id", required = true) @PathVariable Long ticketId,
            @Valid @RequestBody TicketRequestDTO request) {
        LOGGER.debug("PUT /api/ticket/{}", ticketId);
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request));
    }

    @DeleteMapping("/{ticketId}")
    @Operation(summary = "Delete ticket (Client)",
            description = "Deletes the client's own ticket while it is Open and unassigned; returns the deleted ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket deleted",
                    content = @Content(schema = @Schema(implementation = TicketResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Ticket cannot be deleted (assigned / not Open)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<TicketResponseDTO> deleteTicket(
            @Parameter(description = "Ticket id", required = true) @PathVariable Long ticketId) {
        LOGGER.debug("DELETE /api/ticket/{}", ticketId);
        return ResponseEntity.ok(ticketService.deleteTicket(ticketId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "View tickets by user id (Client)",
            description = "Returns the tickets of the authenticated client. userId must match the JWT identity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tickets of the user",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketResponseDTO.class)))),
            @ApiResponse(responseCode = "403", description = "Manager, or another client's userId",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<List<TicketResponseDTO>> getTicketsByUserId(
            @Parameter(description = "User id", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(ticketService.getTicketsByUserId(userId));
    }

    @GetMapping("/agent/{agentId}")
    @Operation(summary = "View tickets by agent id (Client)",
            description = "Returns the authenticated client's tickets handled by the given agent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tickets of the agent",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketResponseDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<List<TicketResponseDTO>> getTicketsByAgentId(
            @Parameter(description = "Support agent id", required = true) @PathVariable Long agentId) {
        return ResponseEntity.ok(ticketService.getTicketsByAgentId(agentId));
    }
}
