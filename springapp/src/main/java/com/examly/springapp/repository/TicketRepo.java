package com.examly.springapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.examly.springapp.model.Ticket;

@Repository
public interface TicketRepo extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserUserId(Long userId);

    List<Ticket> findBySupportAgentAgentId(Long agentId);

    List<Ticket> findBySupportAgentAgentIdAndUserUserId(Long agentId, Long userId);

    boolean existsByUserUserIdAndTitleIgnoreCase(Long userId, String title);

    boolean existsByUserUserIdAndTitleIgnoreCaseAndTicketIdNot(Long userId, String title, Long ticketId);

    boolean existsBySupportAgentAgentId(Long agentId);
}
