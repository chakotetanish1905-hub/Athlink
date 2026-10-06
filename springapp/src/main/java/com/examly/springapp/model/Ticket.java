package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must not exceed 120 characters")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(nullable = false, length = 2000)
    private String description;

    // High, Medium or Low (stored as the label, see TicketPriority)
    @NotNull(message = "Priority is required")
    @Convert(converter = TicketPriority.DbConverter.class)
    @Column(nullable = false)
    private TicketPriority priority;

    // Open, In Progress, Resolved or Closed. Every new ticket starts as Open.
    @Convert(converter = TicketStatus.DbConverter.class)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = true)
    private LocalDate resolutionDate;

    @NotBlank(message = "Issue category is required")
    private String issueCategory;

    // Optional at first, but required before the ticket can be marked Resolved.
    @Column(nullable = true, length = 2000)
    private String resolutionSummary;

    @Column(nullable = true)
    private Boolean satisfied;

    // The client who raised the ticket.
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The support agent assigned by the manager (empty until assigned).
    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = true)
    private SupportAgent supportAgent;

    public Ticket() {
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

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SupportAgent getSupportAgent() {
        return supportAgent;
    }

    public void setSupportAgent(SupportAgent supportAgent) {
        this.supportAgent = supportAgent;
    }
}
