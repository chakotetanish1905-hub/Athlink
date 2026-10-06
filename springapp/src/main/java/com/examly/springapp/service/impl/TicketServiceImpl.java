package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.AgentStatus;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.TicketStatus;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.TicketService;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketServiceImpl.class);

    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;
    private final SupportAgentRepo supportAgentRepo;
    private final FeedbackRepo feedbackRepo;

    public TicketServiceImpl(TicketRepo ticketRepo, UserRepo userRepo, SupportAgentRepo supportAgentRepo,
            FeedbackRepo feedbackRepo) {
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.supportAgentRepo = supportAgentRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    @Transactional
    public Ticket addTicket(Ticket ticket) {
        Long userId = ticket.getUser().getUserId();
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        String title = ticket.getTitle().trim();
        if (ticketRepo.existsByUserUserIdAndTitleIgnoreCase(userId, title)) {
            throw new DuplicateTicketException("A ticket with this title already exists");
        }

        // Every new ticket starts as Open, without an agent and without a resolution
        ticket.setTicketId(null);
        ticket.setTitle(title);
        ticket.setUser(user);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedDate(LocalDate.now());
        ticket.setResolutionDate(null);
        ticket.setResolutionSummary(null);
        ticket.setSatisfied(null);
        ticket.setSupportAgent(null);

        Ticket saved = ticketRepo.save(ticket);
        LOGGER.info("Ticket created: ticketId={} userId={} priority={} category={}",
                saved.getTicketId(), userId, saved.getPriority().getLabel(), saved.getIssueCategory());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> getTicketById(Long ticketId) {
        return ticketRepo.findById(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return ticketRepo.findAll();
    }

    @Override
    @Transactional
    public Ticket updateTicket(Long ticketId, Ticket ticket) {
        Ticket existing = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + ticketId));

        // 1. Ticket details can only change while the ticket is Open and no agent is assigned
        boolean detailsChanged = !existing.getTitle().equals(ticket.getTitle().trim())
                || !existing.getDescription().equals(ticket.getDescription())
                || existing.getPriority() != ticket.getPriority()
                || !existing.getIssueCategory().equals(ticket.getIssueCategory());
        if (detailsChanged) {
            if (existing.getStatus() != TicketStatus.OPEN || existing.getSupportAgent() != null) {
                throw new IllegalArgumentException(
                        "A ticket can only be edited while it is Open and no agent is assigned");
            }
            String title = ticket.getTitle().trim();
            if (ticketRepo.existsByUserUserIdAndTitleIgnoreCaseAndTicketIdNot(
                    existing.getUser().getUserId(), title, ticketId)) {
                throw new DuplicateTicketException("A ticket with this title already exists");
            }
            existing.setTitle(title);
            existing.setDescription(ticket.getDescription());
            existing.setPriority(ticket.getPriority());
            existing.setIssueCategory(ticket.getIssueCategory());
            LOGGER.info("Ticket details updated: ticketId={}", ticketId);
        }

        // 2. Assign a support agent (only an Available agent can be assigned)
        if (ticket.getSupportAgent() != null && ticket.getSupportAgent().getAgentId() != null) {
            Long newAgentId = ticket.getSupportAgent().getAgentId();
            SupportAgent currentAgent = existing.getSupportAgent();
            if (currentAgent == null || !currentAgent.getAgentId().equals(newAgentId)) {
                SupportAgent agent = supportAgentRepo.findById(newAgentId)
                        .orElseThrow(() -> new NoSuchElementException("Support agent not found with id " + newAgentId));
                if (agent.getStatus() != AgentStatus.AVAILABLE) {
                    throw new IllegalArgumentException("This support agent is currently unavailable");
                }
                existing.setSupportAgent(agent);
                LOGGER.info("Agent assigned: ticketId={} agentId={}", ticketId, newAgentId);
            }
        }

        // 3. Resolution summary and satisfaction (given by the client)
        if (ticket.getResolutionSummary() != null) {
            existing.setResolutionSummary(ticket.getResolutionSummary().trim());
        }
        if (ticket.getSatisfied() != null) {
            existing.setSatisfied(ticket.getSatisfied());
        }

        // 4. Status change
        TicketStatus newStatus = ticket.getStatus();
        if (newStatus != null && newStatus != existing.getStatus()) {
            TicketStatus oldStatus = existing.getStatus();
            validateStatusTransition(existing, newStatus);
            if (newStatus == TicketStatus.RESOLVED) {
                existing.setResolutionDate(LocalDate.now());
            }
            existing.setStatus(newStatus);
            LOGGER.info("Ticket status changed: ticketId={} {} -> {}", ticketId, oldStatus.getLabel(), newStatus.getLabel());
        }

        return ticketRepo.save(existing);
    }

    @Override
    @Transactional
    public Ticket deleteTicket(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + ticketId));

        if (ticket.getStatus() != TicketStatus.OPEN || ticket.getSupportAgent() != null) {
            throw new TicketDeletionException("Only an Open ticket without an assigned agent can be deleted");
        }
        if (feedbackRepo.existsByTicketTicketId(ticketId)) {
            throw new TicketDeletionException("This ticket has feedback and cannot be deleted");
        }
        ticketRepo.delete(ticket);
        LOGGER.info("Ticket deleted: ticketId={}", ticketId);
        return ticket;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByAgentId(Long agentId) {
        checkAgentExists(agentId);
        return ticketRepo.findBySupportAgentAgentId(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByAgentIdForUser(Long agentId, Long userId) {
        checkAgentExists(agentId);
        return ticketRepo.findBySupportAgentAgentIdAndUserUserId(agentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return ticketRepo.findByUserUserId(userId);
    }

    // All status rules in one place. Ticket lifecycle:
    //   Open <-> In Progress  ->  Resolved (by the client, needs a summary and an agent)  ->  Closed (only from Resolved)
    // A Resolved or Closed ticket can never go back to Open or In Progress.
    private void validateStatusTransition(Ticket ticket, TicketStatus newStatus) {
        TicketStatus currentStatus = ticket.getStatus();

        if (currentStatus.isResolvedOrClosed()
                && !(currentStatus == TicketStatus.RESOLVED && newStatus == TicketStatus.CLOSED)) {
            throw new IllegalArgumentException("A resolved or closed ticket cannot be reopened");
        }

        if (newStatus == TicketStatus.RESOLVED) {
            if (ticket.getResolutionSummary() == null || ticket.getResolutionSummary().isBlank()) {
                throw new IllegalArgumentException("Please provide resolution details before marking resolve.");
            }
            if (ticket.getSupportAgent() == null) {
                throw new IllegalArgumentException("A support agent must be assigned before resolving the ticket");
            }
        }

        if (newStatus == TicketStatus.CLOSED && currentStatus != TicketStatus.RESOLVED) {
            throw new IllegalArgumentException("Only a resolved ticket can be closed");
        }
    }

    private void checkAgentExists(Long agentId) {
        if (!supportAgentRepo.existsById(agentId)) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }
    }
}
