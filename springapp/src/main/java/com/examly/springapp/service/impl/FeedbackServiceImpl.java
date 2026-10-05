package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.FeedbackService;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepo feedbackRepo;
    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;

    public FeedbackServiceImpl(FeedbackRepo feedbackRepo, TicketRepo ticketRepo, UserRepo userRepo) {
        this.feedbackRepo = feedbackRepo;
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
    }

    @Override
    public Feedback createFeedback(Feedback feedback) {
        if (feedback.getTicket() == null || feedback.getTicket().getTicketId() == null) {
            throw new IllegalArgumentException("Please select the ticket you are reviewing");
        }

        Ticket ticket = ticketRepo.findById(feedback.getTicket().getTicketId()).orElse(null);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found");
        }
        User user = userRepo.findById(feedback.getUser().getUserId()).orElse(null);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        // A client can only review their own ticket
        if (!ticket.getUser().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("You can only give feedback on your own tickets");
        }
        // Feedback is allowed only after the ticket is Resolved or Closed
        if (!"Resolved".equals(ticket.getStatus()) && !"Closed".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("Feedback can be given only for a Resolved or Closed ticket");
        }
        // One feedback per ticket
        if (!feedbackRepo.findByTicketTicketId(ticket.getTicketId()).isEmpty()) {
            throw new IllegalStateException("You have already given feedback for this ticket");
        }

        feedback.setFeedbackId(null);
        feedback.setUser(user);
        feedback.setTicket(ticket);
        feedback.setSupportAgent(ticket.getSupportAgent());
        feedback.setDate(LocalDate.now());
        return feedbackRepo.save(feedback);
    }

    @Override
    public Feedback getFeedbackById(Long feedbackId) {
        Feedback feedback = feedbackRepo.findById(feedbackId).orElse(null);
        if (feedback == null) {
            throw new NoSuchElementException("Feedback not found with id " + feedbackId);
        }
        return feedback;
    }

    @Override
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepo.findAll();
    }

    @Override
    public Feedback deleteFeedback(Long feedbackId) {
        Feedback feedback = getFeedbackById(feedbackId);
        feedbackRepo.delete(feedback);
        return feedback;
    }

    @Override
    public List<Feedback> getFeedbacksByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return feedbackRepo.findByUserUserId(userId);
    }
}
