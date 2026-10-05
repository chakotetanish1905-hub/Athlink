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
import com.examly.springapp.model.SupportAgentRequestDTO;
import com.examly.springapp.model.SupportAgentResponseDTO;
import com.examly.springapp.service.SupportAgentService;

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
@RequestMapping("/api/supportAgent")
@Tag(name = "Support Agents", description = "Support agent management")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class SupportAgentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportAgentController.class);

    private final SupportAgentService supportAgentService;

    public SupportAgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    @PostMapping
    @Operation(summary = "Add support agent (Manager)", description = "Creates a support agent.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "name, email, phone (10 digits), expertise, experience, status, shiftTiming, "
                    + "optional addedDate/profile (Base64)/remarks")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agent created",
                    content = @Content(schema = @Schema(implementation = SupportAgentResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Client",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate email or phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<SupportAgentResponseDTO> addSupportAgent(
            @Valid @RequestBody SupportAgentRequestDTO request) {
        LOGGER.debug("POST /api/supportAgent");
        return ResponseEntity.status(HttpStatus.CREATED).body(supportAgentService.addSupportAgent(request));
    }

    @GetMapping("/{agentId}")
    @Operation(summary = "View support agent by id (Manager, Client)", description = "Returns one support agent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent found",
                    content = @Content(schema = @Schema(implementation = SupportAgentResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<SupportAgentResponseDTO> getSupportAgentById(
            @Parameter(description = "Support agent id", required = true) @PathVariable Long agentId) {
        return ResponseEntity.ok(supportAgentService.getSupportAgentById(agentId));
    }

    @GetMapping
    @Operation(summary = "View all support agents (Manager)", description = "Returns every support agent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agents found",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SupportAgentResponseDTO.class)))),
            @ApiResponse(responseCode = "204", description = "No agents"),
            @ApiResponse(responseCode = "403", description = "Forbidden for Client",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<List<SupportAgentResponseDTO>> getAllSupportAgents() {
        List<SupportAgentResponseDTO> agents = supportAgentService.getAllSupportAgents();
        if (agents.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(agents);
    }

    @PutMapping("/{agentId}")
    @Operation(summary = "Update support agent (Manager)",
            description = "Updates an agent, including the Available/Unavailable toggle.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Full agent payload")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent updated",
                    content = @Content(schema = @Schema(implementation = SupportAgentResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Client",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate email or phone",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<SupportAgentResponseDTO> updateSupportAgent(
            @Parameter(description = "Support agent id", required = true) @PathVariable Long agentId,
            @Valid @RequestBody SupportAgentRequestDTO request) {
        LOGGER.debug("PUT /api/supportAgent/{}", agentId);
        return ResponseEntity.ok(supportAgentService.updateSupportAgent(agentId, request));
    }

    @DeleteMapping("/{agentId}")
    @Operation(summary = "Delete support agent (Manager)",
            description = "Deletes an agent that has no assigned tickets; returns the deleted agent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent deleted",
                    content = @Content(schema = @Schema(implementation = SupportAgentResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Client",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Agent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Agent has assigned tickets",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<SupportAgentResponseDTO> deleteSupportAgent(
            @Parameter(description = "Support agent id", required = true) @PathVariable Long agentId) {
        LOGGER.debug("DELETE /api/supportAgent/{}", agentId);
        return ResponseEntity.ok(supportAgentService.deleteSupportAgent(agentId));
    }
}
