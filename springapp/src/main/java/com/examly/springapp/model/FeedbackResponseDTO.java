package com.examly.springapp.model;

import java.time.LocalDate;

/**
 * Feedback as returned by the API, including the related user, agent and ticket
 * so the UI can show "View Ticket Info" / "View Agent Info" / "Show User Profile".
 */
public class FeedbackResponseDTO {

    private Long feedbackId;
    private String feedbackText;
    private LocalDate date;
    private Long userId;
    private Long agentId;
    private Long ticketId;
    private String category;
    private Integer rating;
    private UserResponseDTO user;
    private SupportAgentResponseDTO supportAgent;
    private TicketResponseDTO ticket;

    public FeedbackResponseDTO() {
    }

    public static FeedbackResponseDTO from(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        FeedbackResponseDTO dto = new FeedbackResponseDTO();
        dto.setFeedbackId(feedback.getFeedbackId());
        dto.setFeedbackText(feedback.getFeedbackText());
        dto.setDate(feedback.getDate());
        dto.setCategory(feedback.getCategory());
        dto.setRating(feedback.getRating());
        if (feedback.getUser() != null) {
            dto.setUserId(feedback.getUser().getUserId());
            dto.setUser(UserResponseDTO.from(feedback.getUser()));
        }
        if (feedback.getSupportAgent() != null) {
            dto.setAgentId(feedback.getSupportAgent().getAgentId());
            dto.setSupportAgent(SupportAgentResponseDTO.from(feedback.getSupportAgent()));
        }
        if (feedback.getTicket() != null) {
            dto.setTicketId(feedback.getTicket().getTicketId());
            dto.setTicket(TicketResponseDTO.from(feedback.getTicket()));
        }
        return dto;
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
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

    public UserResponseDTO getUser() {
        return user;
    }

    public void setUser(UserResponseDTO user) {
        this.user = user;
    }

    public SupportAgentResponseDTO getSupportAgent() {
        return supportAgent;
    }

    public void setSupportAgent(SupportAgentResponseDTO supportAgent) {
        this.supportAgent = supportAgent;
    }

    public TicketResponseDTO getTicket() {
        return ticket;
    }

    public void setTicket(TicketResponseDTO ticket) {
        this.ticket = ticket;
    }
}
