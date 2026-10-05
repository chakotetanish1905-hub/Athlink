package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.SupportAgent;

public interface SupportAgentRepo extends JpaRepository<SupportAgent, Long> {

    // Used to throw DuplicateAgentException when the email is already taken.
    SupportAgent findByEmail(String email);
}
