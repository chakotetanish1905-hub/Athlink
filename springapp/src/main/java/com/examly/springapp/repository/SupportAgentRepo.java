package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.SupportAgent;

public interface SupportAgentRepo extends JpaRepository<SupportAgent, Long> {

    // Add agent: is the email already used? (DuplicateAgentException)
    boolean existsByEmail(String email);

    // Update agent: is the email used by ANOTHER agent?
    boolean existsByEmailAndAgentIdNot(String email, Long agentId);
}
