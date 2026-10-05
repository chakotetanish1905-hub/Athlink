package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Feedback;

public interface FeedbackRepo extends JpaRepository<Feedback, Long> {

    // GET /api/feedback/user/{userId}
    List<Feedback> findByUserUserId(Long userId);

    // Used before deleting a ticket or an agent that still has feedback.
    List<Feedback> findByTicketTicketId(Long ticketId);

    List<Feedback> findBySupportAgentAgentId(Long agentId);
}
