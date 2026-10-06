package com.examly.springapp.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.TicketStatus;
import com.examly.springapp.model.User;
import com.examly.springapp.service.TicketService;

import jakarta.validation.Valid;

// The URL role rules are in SecurityConfig. This controller adds the ownership checks:
// a Client can only see and change their own tickets (the userId in the URL is never trusted).
@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Client: 201 with the new ticket
    @PostMapping
    public ResponseEntity<Ticket> addTicket(@Valid @RequestBody Ticket ticket,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        // The ticket always belongs to the logged-in client
        User owner = new User();
        owner.setUserId(currentUser.getUserId());
        ticket.setUser(owner);

        Ticket savedTicket = ticketService.addTicket(ticket);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTicket);
    }

    // Client: 200 with the ticket, 404 if not found
    @GetMapping("/{ticketId}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long ticketId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket ticket = findTicket(ticketId);
        checkOwner(ticket, currentUser);
        return ResponseEntity.ok(ticket);
    }

    // Manager: every ticket. Client: only their own tickets. 204 when there are none.
    @GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets(@AuthenticationPrincipal UserPrinciple currentUser) {
        List<Ticket> tickets;
        if (currentUser != null && currentUser.isClient()) {
            tickets = ticketService.getTicketsByUserId(currentUser.getUserId());
        } else {
            tickets = ticketService.getAllTickets();
        }

        if (tickets.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tickets);
    }

    // Manager: assign an agent or close the ticket.
    // Client: edit their Open ticket, add the resolution summary, mark it Resolved.
    @PutMapping("/{ticketId}")
    public ResponseEntity<Ticket> updateTicket(@PathVariable Long ticketId, @Valid @RequestBody Ticket ticket,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket existing = findTicket(ticketId);

        if (currentUser.isManager()) {
            // A manager only assigns agents and changes the status, so keep the client's fields as they are
            ticket.setTitle(existing.getTitle());
            ticket.setDescription(existing.getDescription());
            ticket.setPriority(existing.getPriority());
            ticket.setIssueCategory(existing.getIssueCategory());
            ticket.setResolutionSummary(existing.getResolutionSummary());
            ticket.setSatisfied(existing.getSatisfied());
        } else {
            checkOwner(existing, currentUser);
            // A client cannot assign an agent, close a ticket or set it to In Progress
            ticket.setSupportAgent(existing.getSupportAgent());
            TicketStatus newStatus = ticket.getStatus();
            boolean statusChanged = newStatus != null && newStatus != existing.getStatus();
            if (statusChanged && (newStatus == TicketStatus.CLOSED || newStatus == TicketStatus.IN_PROGRESS)) {
                throw new AccessDeniedException("Only a manager can change the ticket to " + newStatus.getLabel());
            }
        }

        Ticket updatedTicket = ticketService.updateTicket(ticketId, ticket);
        return ResponseEntity.ok(updatedTicket);
    }

    // Client: 200 with the deleted ticket
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Ticket> deleteTicket(@PathVariable Long ticketId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Ticket existing = findTicket(ticketId);
        checkOwner(existing, currentUser);

        Ticket deletedTicket = ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok(deletedTicket);
    }

    // Client: their own tickets only
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Ticket>> getTicketsByUserId(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        if (!userId.equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only view your own tickets");
        }
        return ResponseEntity.ok(ticketService.getTicketsByUserId(userId));
    }

    // Client: their own tickets that were handled by this agent ("Tickets Worked")
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<List<Ticket>> getTicketsByAgentId(@PathVariable Long agentId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        // The query only returns this client's tickets, so no other client's data is loaded
        return ResponseEntity.ok(ticketService.getTicketsByAgentIdForUser(agentId, currentUser.getUserId()));
    }

    // 404 (standard error body, saved in ErrorLogs) when the ticket does not exist
    private Ticket findTicket(Long ticketId) {
        return ticketService.getTicketById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found with id " + ticketId));
    }

    // Throws 403 when a client tries to use somebody else's ticket.
    private void checkOwner(Ticket ticket, UserPrinciple currentUser) {
        if (!ticket.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only access your own tickets");
        }
    }
}
