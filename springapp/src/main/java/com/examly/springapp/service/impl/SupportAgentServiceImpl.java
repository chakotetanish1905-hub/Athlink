package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.AgentDeletionException;
import com.examly.springapp.exceptions.DuplicateAgentException;
import com.examly.springapp.model.AgentStatus;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.service.SupportAgentService;

@Service
public class SupportAgentServiceImpl implements SupportAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportAgentServiceImpl.class);

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
    @Transactional
    public SupportAgent addSupportAgent(SupportAgent supportAgent) {
        String email = supportAgent.getEmail().trim().toLowerCase();

        // Two agents cannot share the same email
        if (supportAgentRepo.existsByEmail(email)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        supportAgent.setAgentId(null);
        supportAgent.setEmail(email);
        supportAgent.setAddedDate(LocalDate.now());
        SupportAgent saved = supportAgentRepo.save(supportAgent);
        LOGGER.info("Support agent added: agentId={} expertise={} status={}",
                saved.getAgentId(), saved.getExpertise(), saved.getStatus().getLabel());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupportAgent> getSupportAgentById(Long agentId) {
        return supportAgentRepo.findById(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportAgent> getAllSupportAgents() {
        return supportAgentRepo.findAll();
    }

    @Override
    @Transactional
    public SupportAgent updateSupportAgent(Long agentId, SupportAgent supportAgent) {
        SupportAgent existing = supportAgentRepo.findById(agentId)
                .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + agentId));

        String email = supportAgent.getEmail().trim().toLowerCase();
        if (supportAgentRepo.existsByEmailAndAgentIdNot(email, agentId)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }

        AgentStatus oldStatus = existing.getStatus();
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
        SupportAgent saved = supportAgentRepo.save(existing);
        LOGGER.info("Support agent updated: agentId={}", agentId);
        if (oldStatus != saved.getStatus()) {
            LOGGER.info("Support agent availability changed: agentId={} {} -> {}",
                    agentId, oldStatus.getLabel(), saved.getStatus().getLabel());
        }
        return saved;
    }

    @Override
    @Transactional
    public SupportAgent deleteSupportAgent(Long agentId) {
        SupportAgent agent = supportAgentRepo.findById(agentId)
                .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + agentId));

        // An agent that worked on tickets (or received feedback) is kept for the ticket history
        if (ticketRepo.existsBySupportAgentAgentId(agentId) || feedbackRepo.existsBySupportAgentAgentId(agentId)) {
            throw new AgentDeletionException(
                    "This agent is assigned to tickets and cannot be deleted. Mark the agent Unavailable instead.");
        }

        supportAgentRepo.delete(agent);
        LOGGER.info("Support agent deleted: agentId={}", agentId);
        return agent;
    }
}
