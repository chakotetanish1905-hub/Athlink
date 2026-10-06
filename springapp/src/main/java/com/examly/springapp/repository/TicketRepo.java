package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.Ticket;

public interface TicketRepo extends JpaRepository<Ticket, Long> {

    // GET /api/ticket/user/{userId}
    List<Ticket> findByUserUserId(Long userId);

    // TicketService.getTicketsByAgentId (SRS): every ticket handled by the agent
    List<Ticket> findBySupportAgentAgentId(Long agentId);

    // GET /api/ticket/agent/{agentId} - the logged-in client's tickets handled by the agent
    List<Ticket> findBySupportAgentAgentIdAndUserUserId(Long agentId, Long userId);

    // Duplicate title check when a client creates a ticket (DuplicateTicketException)
    boolean existsByUserUserIdAndTitleIgnoreCase(Long userId, String title);

    // Duplicate title check when a client edits a ticket (ignores the ticket being edited)
    boolean existsByUserUserIdAndTitleIgnoreCaseAndTicketIdNot(Long userId, String title, Long ticketId);

    // An agent who worked on tickets cannot be deleted (AgentDeletionException)
    boolean existsBySupportAgentAgentId(Long agentId);
}
