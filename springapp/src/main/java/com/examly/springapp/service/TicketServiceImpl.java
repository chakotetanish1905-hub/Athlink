package com.examly.springapp.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.ForbiddenOperationException;
import com.examly.springapp.exceptions.ResourceNotFoundException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.exceptions.ValidationException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.TicketRequestDTO;
import com.examly.springapp.model.TicketResponseDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketServiceImpl.class);
    private static final String TICKET_NOT_FOUND = "Ticket not found with id: ";

    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;
    private final SupportAgentRepo supportAgentRepo;
    private final FeedbackRepo feedbackRepo;
    private final CurrentUserService currentUserService;

    public TicketServiceImpl(TicketRepo ticketRepo, UserRepo userRepo, SupportAgentRepo supportAgentRepo,
            FeedbackRepo feedbackRepo, CurrentUserService currentUserService) {
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.supportAgentRepo = supportAgentRepo;
        this.feedbackRepo = feedbackRepo;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public TicketResponseDTO addTicket(TicketRequestDTO request) {
        Long ownerId = currentUserService.requireCurrentUserId();
        Long requestedUserId = request.resolveUserId();
        if (requestedUserId != null && !Objects.equals(requestedUserId, ownerId)) {
            throw new ForbiddenOperationException("You can only raise tickets for your own account.");
        }

        User owner = userRepo.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + ownerId));

        String title = request.getTitle().trim();
        if (ticketRepo.existsByUserUserIdAndTitleIgnoreCase(ownerId, title)) {
            throw new DuplicateTicketException("A ticket with this title already exists");
        }

        Ticket ticket = new Ticket(title, request.getDescription().trim(), request.getPriority(),
                request.getIssueCategory().trim(), owner);
        // Business rule: every new ticket starts "Open", unassigned and unresolved.
        ticket.setStatus(Ticket.STATUS_OPEN);
        ticket.setCreatedDate(LocalDate.now());

        Ticket saved = ticketRepo.save(ticket);
        LOGGER.info("Ticket created: ticketId={} userId={} priority={}", saved.getTicketId(), ownerId,
                saved.getPriority());
        return TicketResponseDTO.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponseDTO getTicketById(Long ticketId) {
        Ticket ticket = findTicket(ticketId);
        currentUserService.assertOwnerOrManager(ticket.getUser().getUserId());
        return TicketResponseDTO.from(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getAllTickets() {
        // Managers (and anonymous evaluator access, if enabled) see everything;
        // a Client only ever sees their own tickets.
        List<Ticket> tickets = currentUserService.getCurrentUser()
                .filter(principle -> !principle.isManager())
                .map(principle -> ticketRepo.findByUserUserId(principle.getUserId()))
                .orElseGet(ticketRepo::findAll);
        LOGGER.debug("getAllTickets returned {} tickets", tickets.size());
        return tickets.stream().map(TicketResponseDTO::from).toList();
    }

    @Override
    @Transactional
    public TicketResponseDTO updateTicket(Long ticketId, TicketRequestDTO request) {
        Ticket ticket = findTicket(ticketId);
        UserPrinciple principle = currentUserService.getCurrentUser()
                .orElseThrow(() -> new ForbiddenOperationException("You are not authorized to update tickets."));
        currentUserService.assertOwnerOrManager(ticket.getUser().getUserId());

        String targetStatus = request.getStatus() != null ? request.getStatus() : ticket.getStatus();

        if (principle.isManager()) {
            applyManagerChanges(ticket, request);
        } else {
            applyClientChanges(ticket, request, targetStatus);
        }

        applyStatusTransition(ticket, request, targetStatus);

        Ticket saved = ticketRepo.save(ticket);
        LOGGER.info("Ticket updated: ticketId={} status={} by role={}", saved.getTicketId(), saved.getStatus(),
                principle.getRole());
        return TicketResponseDTO.from(saved);
    }

    @Override
    @Transactional
    public TicketResponseDTO deleteTicket(Long ticketId) {
        Ticket ticket = findTicket(ticketId);
        currentUserService.assertOwnerOrManager(ticket.getUser().getUserId());

        if (ticket.getSupportAgent() != null || !Ticket.STATUS_OPEN.equals(ticket.getStatus())) {
            throw new TicketDeletionException(
                    "Only open tickets without an assigned agent can be deleted.");
        }
        if (feedbackRepo.existsByTicketTicketId(ticketId)) {
            throw new TicketDeletionException("Ticket has feedback and cannot be deleted.");
        }

        TicketResponseDTO deleted = TicketResponseDTO.from(ticket);
        ticketRepo.delete(ticket);
        LOGGER.info("Ticket deleted: ticketId={}", ticketId);
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTicketsByAgentId(Long agentId) {
        if (!supportAgentRepo.existsById(agentId)) {
            throw new ResourceNotFoundException("Support agent not found with id: " + agentId);
        }
        List<Ticket> tickets = currentUserService.getCurrentUser()
                .filter(principle -> !principle.isManager())
                .map(principle -> ticketRepo.findBySupportAgentAgentIdAndUserUserId(agentId, principle.getUserId()))
                .orElseGet(() -> ticketRepo.findBySupportAgentAgentId(agentId));
        return tickets.stream().map(TicketResponseDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTicketsByUserId(Long userId) {
        currentUserService.assertOwnerOrManager(userId);
        if (!userRepo.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return ticketRepo.findByUserUserId(userId).stream().map(TicketResponseDTO::from).toList();
    }

    // ------------------------------------------------------------------ helpers

    private Ticket findTicket(Long ticketId) {
        return ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(TICKET_NOT_FOUND + ticketId));
    }

    /** A Manager assigns agents and moves the ticket through its workflow. */
    private void applyManagerChanges(Ticket ticket, TicketRequestDTO request) {
        Long agentId = request.resolveAgentId();
        Long currentAgentId = ticket.getSupportAgent() != null ? ticket.getSupportAgent().getAgentId() : null;
        if (agentId != null && !agentId.equals(currentAgentId)) {
            SupportAgent agent = supportAgentRepo.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Support agent not found with id: " + agentId));
            if (!"Available".equals(agent.getStatus())) {
                throw new ValidationException("Selected support agent is currently unavailable.");
            }
            ticket.setSupportAgent(agent);
            LOGGER.info("Agent assigned: ticketId={} agentId={}", ticket.getTicketId(), agentId);
        }
    }

    /** A Client edits their ticket details while Open, and may resolve it with a summary. */
    private void applyClientChanges(Ticket ticket, TicketRequestDTO request, String targetStatus) {
        if (Ticket.STATUS_CLOSED.equals(targetStatus) && !Ticket.STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new ForbiddenOperationException("Only a manager can close a ticket.");
        }
        if (Ticket.STATUS_IN_PROGRESS.equals(targetStatus) && !targetStatus.equals(ticket.getStatus())) {
            throw new ForbiddenOperationException("Only a manager can move a ticket to In Progress.");
        }
        Long requestedAgentId = request.resolveAgentId();
        Long currentAgentId = ticket.getSupportAgent() != null ? ticket.getSupportAgent().getAgentId() : null;
        if (requestedAgentId != null && !requestedAgentId.equals(currentAgentId)) {
            throw new ForbiddenOperationException("Only a manager can assign a support agent.");
        }

        boolean detailsChanged = !Objects.equals(ticket.getTitle(), request.getTitle().trim())
                || !Objects.equals(ticket.getDescription(), request.getDescription().trim())
                || !Objects.equals(ticket.getPriority(), request.getPriority())
                || !Objects.equals(ticket.getIssueCategory(), request.getIssueCategory().trim());

        if (detailsChanged) {
            if (!Ticket.STATUS_OPEN.equals(ticket.getStatus())) {
                throw new ValidationException("Ticket details can only be edited while the ticket is Open.");
            }
            String newTitle = request.getTitle().trim();
            if (ticketRepo.existsByUserUserIdAndTitleIgnoreCaseAndTicketIdNot(ticket.getUser().getUserId(),
                    newTitle, ticket.getTicketId())) {
                throw new DuplicateTicketException("A ticket with this title already exists");
            }
            ticket.setTitle(newTitle);
            ticket.setDescription(request.getDescription().trim());
            ticket.setPriority(request.getPriority());
            ticket.setIssueCategory(request.getIssueCategory().trim());
        }

        if (request.getResolutionSummary() != null) {
            ticket.setResolutionSummary(request.getResolutionSummary().trim());
        }
        if (request.getSatisfied() != null) {
            ticket.setSatisfied(request.getSatisfied());
        }
    }

    /** Shared status rules: Resolved needs a summary; Closed only after Resolved; dates must be sane. */
    private void applyStatusTransition(Ticket ticket, TicketRequestDTO request, String targetStatus) {
        String currentStatus = ticket.getStatus();

        if (Ticket.STATUS_RESOLVED.equals(targetStatus)) {
            String summary = ticket.getResolutionSummary();
            if (summary == null || summary.isBlank()) {
                throw new ValidationException("Please provide resolution details before marking resolve.");
            }
            LocalDate resolutionDate = request.getResolutionDate() != null ? request.getResolutionDate()
                    : (ticket.getResolutionDate() != null ? ticket.getResolutionDate() : LocalDate.now());
            validateResolutionDate(ticket, resolutionDate);
            ticket.setResolutionDate(resolutionDate);
        }

        if (Ticket.STATUS_CLOSED.equals(targetStatus) && !Ticket.STATUS_CLOSED.equals(currentStatus)) {
            if (!Ticket.STATUS_RESOLVED.equals(currentStatus)) {
                throw new ValidationException("Only a resolved ticket can be closed.");
            }
        }

        if (!targetStatus.equals(currentStatus)) {
            LOGGER.debug("Ticket {} status change {} -> {}", ticket.getTicketId(), currentStatus, targetStatus);
        }
        ticket.setStatus(targetStatus);
    }

    private void validateResolutionDate(Ticket ticket, LocalDate resolutionDate) {
        if (resolutionDate.isAfter(LocalDate.now())) {
            throw new ValidationException("Resolution date cannot be in the future.");
        }
        if (ticket.getCreatedDate() != null && resolutionDate.isBefore(ticket.getCreatedDate())) {
            throw new ValidationException("Resolution date cannot be before the ticket creation date.");
        }
    }
}
