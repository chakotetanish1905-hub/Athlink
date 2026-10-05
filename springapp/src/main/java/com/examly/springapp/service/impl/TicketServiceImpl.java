package com.examly.springapp.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.TicketService;

@Service
public class TicketServiceImpl implements TicketService {

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
    public Ticket addTicket(Ticket ticket) {
        User user = userRepo.findById(ticket.getUser().getUserId()).orElse(null);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        String title = ticket.getTitle().trim();
        if (titleAlreadyUsed(user.getUserId(), title, null)) {
            throw new DuplicateTicketException("A ticket with this title already exists");
        }

        // Every new ticket starts as Open, without an agent and without a resolution
        ticket.setTicketId(null);
        ticket.setTitle(title);
        ticket.setUser(user);
        ticket.setStatus("Open");
        ticket.setCreatedDate(LocalDate.now());
        ticket.setResolutionDate(null);
        ticket.setResolutionSummary(null);
        ticket.setSatisfied(null);
        ticket.setSupportAgent(null);
        return ticketRepo.save(ticket);
    }

    @Override
    public Optional<Ticket> getTicketById(Long ticketId) {
        return ticketRepo.findById(ticketId);
    }

    @Override
    public List<Ticket> getAllTickets() {
        return ticketRepo.findAll();
    }

    @Override
    public Ticket updateTicket(Long ticketId, Ticket ticket) {
        Ticket existing = ticketRepo.findById(ticketId).orElse(null);
        if (existing == null) {
            throw new NoSuchElementException("Ticket not found with id " + ticketId);
        }

        // 1. Ticket details can only change while the ticket is Open and no agent is assigned
        boolean detailsChanged = !existing.getTitle().equals(ticket.getTitle().trim())
                || !existing.getDescription().equals(ticket.getDescription())
                || !existing.getPriority().equals(ticket.getPriority())
                || !existing.getIssueCategory().equals(ticket.getIssueCategory());
        if (detailsChanged) {
            if (!"Open".equals(existing.getStatus()) || existing.getSupportAgent() != null) {
                throw new IllegalArgumentException(
                        "A ticket can only be edited while it is Open and no agent is assigned");
            }
            String title = ticket.getTitle().trim();
            if (titleAlreadyUsed(existing.getUser().getUserId(), title, ticketId)) {
                throw new DuplicateTicketException("A ticket with this title already exists");
            }
            existing.setTitle(title);
            existing.setDescription(ticket.getDescription());
            existing.setPriority(ticket.getPriority());
            existing.setIssueCategory(ticket.getIssueCategory());
        }

        // 2. Assign a support agent (only an Available agent can be assigned)
        if (ticket.getSupportAgent() != null && ticket.getSupportAgent().getAgentId() != null) {
            Long newAgentId = ticket.getSupportAgent().getAgentId();
            SupportAgent currentAgent = existing.getSupportAgent();
            if (currentAgent == null || !currentAgent.getAgentId().equals(newAgentId)) {
                SupportAgent agent = supportAgentRepo.findById(newAgentId).orElse(null);
                if (agent == null) {
                    throw new NoSuchElementException("Support agent not found with id " + newAgentId);
                }
                if (!"Available".equals(agent.getStatus())) {
                    throw new IllegalArgumentException("This support agent is currently unavailable");
                }
                existing.setSupportAgent(agent);
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
        String newStatus = ticket.getStatus();
        if (newStatus != null && !newStatus.equals(existing.getStatus())) {
            changeStatus(existing, newStatus);
        }

        return ticketRepo.save(existing);
    }

    @Override
    public Ticket deleteTicket(Long ticketId) {
        Ticket ticket = ticketRepo.findById(ticketId).orElse(null);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found with id " + ticketId);
        }
        if (!"Open".equals(ticket.getStatus()) || ticket.getSupportAgent() != null) {
            throw new TicketDeletionException("Only an Open ticket without an assigned agent can be deleted");
        }
        if (!feedbackRepo.findByTicketTicketId(ticketId).isEmpty()) {
            throw new TicketDeletionException("This ticket has feedback and cannot be deleted");
        }
        ticketRepo.delete(ticket);
        return ticket;
    }

    @Override
    public List<Ticket> getTicketsByAgentId(Long agentId) {
        if (!supportAgentRepo.existsById(agentId)) {
            throw new NoSuchElementException("Support agent not found with id " + agentId);
        }
        return ticketRepo.findBySupportAgentAgentId(agentId);
    }

    @Override
    public List<Ticket> getTicketsByUserId(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NoSuchElementException("User not found with id " + userId);
        }
        return ticketRepo.findByUserUserId(userId);
    }

    // Ticket lifecycle: Open -> (agent assigned) -> Resolved (by client, needs summary) -> Closed (by manager)
    private void changeStatus(Ticket ticket, String newStatus) {
        String currentStatus = ticket.getStatus();

        if ("Resolved".equals(currentStatus) || "Closed".equals(currentStatus)) {
            if (!("Resolved".equals(currentStatus) && "Closed".equals(newStatus))) {
                throw new IllegalArgumentException("A resolved or closed ticket cannot be reopened");
            }
        }

        if ("Resolved".equals(newStatus)) {
            if (ticket.getResolutionSummary() == null || ticket.getResolutionSummary().isBlank()) {
                throw new IllegalArgumentException("Please provide resolution details before marking resolve.");
            }
            if (ticket.getSupportAgent() == null) {
                throw new IllegalArgumentException("A support agent must be assigned before resolving the ticket");
            }
            ticket.setResolutionDate(LocalDate.now());
        }

        if ("Closed".equals(newStatus) && !"Resolved".equals(currentStatus)) {
            throw new IllegalArgumentException("Only a resolved ticket can be closed");
        }

        ticket.setStatus(newStatus);
    }

    // True when the same client already has another ticket with this title.
    private boolean titleAlreadyUsed(Long userId, String title, Long ignoreTicketId) {
        List<Ticket> tickets = ticketRepo.findByUserUserId(userId);
        for (Ticket t : tickets) {
            boolean sameTicket = ignoreTicketId != null && ignoreTicketId.equals(t.getTicketId());
            if (!sameTicket && t.getTitle().equalsIgnoreCase(title)) {
                return true;
            }
        }
        return false;
    }
}
