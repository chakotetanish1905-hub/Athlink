package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Ticket;

public interface TicketRepo extends JpaRepository<Ticket, Long> {

    // GET /api/ticket/user/{userId}
    List<Ticket> findByUserUserId(Long userId);

    // GET /api/ticket/agent/{agentId}
    List<Ticket> findBySupportAgentAgentId(Long agentId);
}
