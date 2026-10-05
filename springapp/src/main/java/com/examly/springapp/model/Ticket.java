package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Stores all information related to support tickets (SRS: Ticket).
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    public static final String STATUS_OPEN = "Open";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED = "Resolved";
    public static final String STATUS_CLOSED = "Closed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false, length = 2000)
    private String description;

    @NotBlank
    @Pattern(regexp = "^(High|Medium|Low)$")
    @Column(nullable = false)
    private String priority;

    @NotBlank
    @Pattern(regexp = "^(Open|In Progress|Resolved|Closed)$")
    @Column(nullable = false)
    private String status = STATUS_OPEN;

    @NotNull
    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = true)
    private LocalDate resolutionDate;

    @NotBlank
    private String issueCategory;

    @Column(nullable = true, length = 2000)
    private String resolutionSummary;

    @Column(nullable = true)
    private Boolean satisfied;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = true)
    private SupportAgent supportAgent;

    public Ticket() {
    }

    public Ticket(String title, String description, String priority, String issueCategory, User user) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.issueCategory = issueCategory;
        this.user = user;
        this.status = STATUS_OPEN;
        this.createdDate = LocalDate.now();
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
