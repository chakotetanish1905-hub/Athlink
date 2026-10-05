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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.config.SwaggerConfig;
import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.model.FeedbackRequestDTO;
import com.examly.springapp.model.FeedbackResponseDTO;
import com.examly.springapp.service.FeedbackService;

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
@RequestMapping("/api/feedback")
@Tag(name = "Feedback", description = "Client feedback")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class FeedbackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @Operation(summary = "Create feedback (Client)",
            description = "Posts feedback for one of the client's own Resolved or Closed tickets.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "feedbackText, ticketId, category, rating (1-5), optional agentId/date")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feedback created",
                    content = @Content(schema = @Schema(implementation = FeedbackResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Manager / another client's ticket",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Feedback for this ticket already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<FeedbackResponseDTO> createFeedback(@Valid @RequestBody FeedbackRequestDTO request) {
        LOGGER.debug("POST /api/feedback");
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.createFeedback(request));
    }

    @GetMapping("/{feedbackId}")
    @Operation(summary = "View feedback by id (Manager, Client)",
            description = "Manager can view any feedback; a Client only their own.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback found",
                    content = @Content(schema = @Schema(implementation = FeedbackResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<FeedbackResponseDTO> getFeedbackById(
            @Parameter(description = "Feedback id", required = true) @PathVariable Long feedbackId) {
        return ResponseEntity.ok(feedbackService.getFeedbackById(feedbackId));
    }

    @GetMapping
    @Operation(summary = "View all feedbacks (Manager, Client)",
            description = "Manager receives all feedback; a Client receives only their own.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback found",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = FeedbackResponseDTO.class)))),
            @ApiResponse(responseCode = "204", description = "No feedback") })
    public ResponseEntity<List<FeedbackResponseDTO>> getAllFeedbacks() {
        List<FeedbackResponseDTO> feedbacks = feedbackService.getAllFeedbacks();
        if (feedbacks.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "View feedbacks by user id (Client)",
            description = "Returns the authenticated client's feedback. userId must match the JWT identity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback found",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = FeedbackResponseDTO.class)))),
            @ApiResponse(responseCode = "204", description = "No feedback"),
            @ApiResponse(responseCode = "403", description = "Manager, or another client's userId",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<List<FeedbackResponseDTO>> getFeedbacksByUserId(
            @Parameter(description = "User id", required = true) @PathVariable Long userId) {
        List<FeedbackResponseDTO> feedbacks = feedbackService.getFeedbacksByUserId(userId);
        if (feedbacks.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    @DeleteMapping("/{feedbackId}")
    @Operation(summary = "Delete feedback (Client)", description = "Deletes the client's own feedback.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback deleted",
                    content = @Content(schema = @Schema(implementation = FeedbackResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden for Manager / another client's feedback",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<FeedbackResponseDTO> deleteFeedback(
            @Parameter(description = "Feedback id", required = true) @PathVariable Long feedbackId) {
        LOGGER.debug("DELETE /api/feedback/{}", feedbackId);
        return ResponseEntity.ok(feedbackService.deleteFeedback(feedbackId));
    }
}
