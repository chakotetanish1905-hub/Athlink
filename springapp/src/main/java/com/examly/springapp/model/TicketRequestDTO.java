package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST/PUT /api/ticket. Field names mirror the SRS Ticket model.
 * The owning user and the assigned agent may be sent flat (userId / agentId) or nested
 * (user.userId / supportAgent.agentId). Ownership is always re-checked against the JWT.
 */
public class TicketRequestDTO {

    @NotBlank(message = "Title is required.")
    @Size(max = 100, message = "Title must not exceed 100 characters.")
    private String title;

    @NotBlank(message = "Description is required.")
    @Size(max = 2000, message = "Description must not exceed 2000 characters.")
    private String description;

    @NotBlank(message = "Priority is required.")
    @Pattern(regexp = "^(High|Medium|Low)$", message = "Priority must be High, Medium or Low.")
    private String priority;

    @Pattern(regexp = "^(Open|In Progress|Resolved|Closed)$",
            message = "Status must be Open, In Progress, Resolved or Closed.")
    private String status;

    @PastOrPresent(message = "Created date cannot be in the future.")
    private LocalDate createdDate;

    @PastOrPresent(message = "Resolution date cannot be in the future.")
    private LocalDate resolutionDate;

    @NotBlank(message = "Issue category is required.")
    @Size(max = 100, message = "Issue category must not exceed 100 characters.")
    private String issueCategory;

    @Size(max = 2000, message = "Resolution summary must not exceed 2000 characters.")
    private String resolutionSummary;

    private Boolean satisfied;

    private Long userId;

    private Long agentId;

    private EntityRefDTO user;

    private EntityRefDTO supportAgent;

    public TicketRequestDTO() {
    }

    public TicketRequestDTO(String title, String description, String priority, String issueCategory) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.issueCategory = issueCategory;
    }

    /** userId from the flat field, falling back to the nested user reference. */
    public Long resolveUserId() {
        if (userId != null) {
            return userId;
        }
        return user != null ? user.getUserId() : null;
    }

    /** agentId from the flat field, falling back to the nested supportAgent reference. */
    public Long resolveAgentId() {
        if (agentId != null) {
            return agentId;
        }
        return supportAgent != null ? supportAgent.getAgentId() : null;
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
}
