package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.examly.springapp.exceptions.AgentDeletionException;
import com.examly.springapp.exceptions.DuplicateAgentException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.service.SupportAgentService;

@Service
public class SupportAgentServiceImpl implements SupportAgentService {

    private final SupportAgentRepo supportAgentRepo;
    private final TicketRepo ticketRepo;
    private final FeedbackRepo feedbackRepo;

    public SupportAgentServiceImpl(SupportAgentRepo supportAgentRepo, TicketRepo ticketRepo,
            FeedbackRepo feedbackRepo) {
        this.supportAgentRepo = supportAgentRepo;
        this.ticketRepo = ticketRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public SupportAgent addSupportAgent(SupportAgent supportAgent) {
        String email = supportAgent.getEmail().trim().toLowerCase();

        // Two agents cannot share the same email
        if (supportAgentRepo.findByEmail(email) != null) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        supportAgent.setAgentId(null);
        supportAgent.setEmail(email);
        supportAgent.setAddedDate(LocalDate.now());
        return supportAgentRepo.save(supportAgent);
    }

    @Override
    public Optional<SupportAgent> getSupportAgentById(Long agentId) {
        return supportAgentRepo.findById(agentId);
    }

    @Override
    public List<SupportAgent> getAllSupportAgents() {
        return supportAgentRepo.findAll();
    }

    @Override
    public SupportAgent updateSupportAgent(Long agentId, SupportAgent supportAgent) {
        SupportAgent existing = supportAgentRepo.findById(agentId).orElse(null);
        if (existing == null) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }

        String email = supportAgent.getEmail().trim().toLowerCase();
        SupportAgent sameEmail = supportAgentRepo.findByEmail(email);
        if (sameEmail != null && !sameEmail.getAgentId().equals(agentId)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        existing.setName(supportAgent.getName());
        existing.setEmail(email);
        existing.setPhone(supportAgent.getPhone());
        existing.setExpertise(supportAgent.getExpertise());
        existing.setExperience(supportAgent.getExperience());
        existing.setStatus(supportAgent.getStatus());
        existing.setProfile(supportAgent.getProfile());
        existing.setShiftTiming(supportAgent.getShiftTiming());
        existing.setRemarks(supportAgent.getRemarks());
        // addedDate never changes after the agent is created
        return supportAgentRepo.save(existing);
    }

    @Override
    public SupportAgent deleteSupportAgent(Long agentId) {
        SupportAgent agent = supportAgentRepo.findById(agentId).orElse(null);
        if (agent == null) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }

        // An agent that worked on tickets (or received feedback) is kept for the ticket history
        if (!ticketRepo.findBySupportAgentAgentId(agentId).isEmpty()
                || !feedbackRepo.findBySupportAgentAgentId(agentId).isEmpty()) {
            throw new AgentDeletionException(
                    "This agent is assigned to tickets and cannot be deleted. Mark the agent Unavailable instead.");
        }

        supportAgentRepo.delete(agent);
        return agent;
    }
}
