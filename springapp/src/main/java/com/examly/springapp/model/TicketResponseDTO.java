package com.examly.springapp.model;

import java.time.LocalDate;

/**
 * Ticket as returned by the API. Includes the flat ids used by the Angular model
 * plus the related user (without password) and assigned agent.
 */
public class TicketResponseDTO {

    private Long ticketId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private LocalDate createdDate;
    private LocalDate resolutionDate;
    private String issueCategory;
    private String resolutionSummary;
    private Boolean satisfied;
    private Long userId;
    private Long agentId;
    private UserResponseDTO user;
    private SupportAgentResponseDTO supportAgent;

    public TicketResponseDTO() {
    }

    public static TicketResponseDTO from(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setTicketId(ticket.getTicketId());
        dto.setTitle(ticket.getTitle());
        dto.setDescription(ticket.getDescription());
        dto.setPriority(ticket.getPriority());
        dto.setStatus(ticket.getStatus());
        dto.setCreatedDate(ticket.getCreatedDate());
        dto.setResolutionDate(ticket.getResolutionDate());
        dto.setIssueCategory(ticket.getIssueCategory());
        dto.setResolutionSummary(ticket.getResolutionSummary());
        dto.setSatisfied(ticket.getSatisfied());
        if (ticket.getUser() != null) {
            dto.setUserId(ticket.getUser().getUserId());
            dto.setUser(UserResponseDTO.from(ticket.getUser()));
        }
        if (ticket.getSupportAgent() != null) {
            dto.setAgentId(ticket.getSupportAgent().getAgentId());
            dto.setSupportAgent(SupportAgentResponseDTO.from(ticket.getSupportAgent()));
        }
        return dto;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }

    public void setResolutionDate(LocalDate resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public String getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(String issueCategory) {
        this.issueCategory = issueCategory;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public void setResolutionSummary(String resolutionSummary) {
        this.resolutionSummary = resolutionSummary;
    }

    public Boolean getSatisfied() {
        return satisfied;
    }

    public void setSatisfied(Boolean satisfied) {
        this.satisfied = satisfied;
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
}
