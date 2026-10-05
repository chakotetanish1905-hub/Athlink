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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Stores all feedback information (SRS: Feedback).
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feedbackId;

    @NotBlank
    @Column(nullable = false, length = 2000)
    private String feedbackText;

    @NotNull
    @Column(name = "feedback_date", nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "agent_id", nullable = true)
    private SupportAgent supportAgent;

    @ManyToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @NotBlank
    private String category;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    public Feedback() {
    }

    public Feedback(String feedbackText, LocalDate date, String category, Integer rating) {
        this.feedbackText = feedbackText;
        this.date = date;
        this.category = category;
        this.rating = rating;
    }

    public Feedback(String feedbackText, LocalDate date, String category, Integer rating,
            User user, SupportAgent supportAgent, Ticket ticket) {
        this(feedbackText, date, category, rating);
        this.user = user;
        this.supportAgent = supportAgent;
        this.ticket = ticket;
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

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
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
