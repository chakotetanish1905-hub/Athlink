package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/feedback. Field names mirror the SRS Feedback model.
 * Ids may be sent flat (userId/agentId/ticketId) or nested (user/supportAgent/ticket).
 */
public class FeedbackRequestDTO {

    @NotBlank(message = "Feedback text is required.")
    @Size(max = 2000, message = "Feedback must not exceed 2000 characters.")
    private String feedbackText;

    @PastOrPresent(message = "Feedback date cannot be in the future.")
    private LocalDate date;

    private Long userId;

    private Long agentId;

    private Long ticketId;

    private EntityRefDTO user;

    private EntityRefDTO supportAgent;

    private EntityRefDTO ticket;

    @NotBlank(message = "Category is required.")
    @Size(max = 100, message = "Category must not exceed 100 characters.")
    private String category;

    @NotNull(message = "Rating is required.")
    @Min(value = 1, message = "Rating must be between 1 and 5.")
    @Max(value = 5, message = "Rating must be between 1 and 5.")
    private Integer rating;

    public FeedbackRequestDTO() {
    }

    public FeedbackRequestDTO(String feedbackText, Long ticketId, String category, Integer rating) {
        this.feedbackText = feedbackText;
        this.ticketId = ticketId;
        this.category = category;
        this.rating = rating;
    }

    public Long resolveUserId() {
        if (userId != null) {
            return userId;
        }
        return user != null ? user.getUserId() : null;
    }

    public Long resolveAgentId() {
        if (agentId != null) {
            return agentId;
        }
        return supportAgent != null ? supportAgent.getAgentId() : null;
    }

    public Long resolveTicketId() {
        if (ticketId != null) {
            return ticketId;
        }
        return ticket != null ? ticket.getTicketId() : null;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public void setFeedbackText(String feedbackText) {
        this.feedbackText = feedbackText;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public EntityRefDTO getUser() {
        return user;
    }

    public void setUser(EntityRefDTO user) {
        this.user = user;
    }

    public EntityRefDTO getSupportAgent() {
        return supportAgent;
    }

    public void setSupportAgent(EntityRefDTO supportAgent) {
        this.supportAgent = supportAgent;
    }

    public EntityRefDTO getTicket() {
        return ticket;
    }

    public void setTicket(EntityRefDTO ticket) {
        this.ticket = ticket;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
