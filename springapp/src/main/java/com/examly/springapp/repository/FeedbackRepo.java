package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Feedback;

public interface FeedbackRepo extends JpaRepository<Feedback, Long> {

    // GET /api/feedback/user/{userId}
    List<Feedback> findByUserUserId(Long userId);

    // One feedback per ticket, and a ticket with feedback cannot be deleted
    boolean existsByTicketTicketId(Long ticketId);

    // An agent with feedback cannot be deleted
    boolean existsBySupportAgentAgentId(Long agentId);
}
