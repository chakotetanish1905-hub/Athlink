package com.examly.springapp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.TicketDeletionException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepo ticketRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private SupportAgentRepo supportAgentRepo;
    @Mock
    private FeedbackRepo feedbackRepo;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private User client;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setUserId(1L);
        client.setUserRole("Client");
    }

    private Ticket ticket(String title) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription("Something is broken and needs a fix");
        ticket.setPriority("High");
        ticket.setIssueCategory("Technical");
        ticket.setUser(client);
        return ticket;
    }

    private SupportAgent agent(Long id, String status) {
        SupportAgent agent = new SupportAgent();
        agent.setAgentId(id);
        agent.setStatus(status);
        return agent;
    }

    @Test
    void newTicketStartsOpenWithoutAgent() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        when(ticketRepo.findByUserUserId(1L)).thenReturn(new ArrayList<>());
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(call -> call.getArgument(0));

        Ticket request = ticket("VPN down");
        request.setStatus("Closed");
        request.setSupportAgent(agent(5L, "Available"));
        Ticket saved = ticketService.addTicket(request);

        assertEquals("Open", saved.getStatus());
        assertNull(saved.getSupportAgent());
        assertNotNull(saved.getCreatedDate());
    }

    @Test
    void duplicateTitleThrowsDuplicateTicketException() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findByUserUserId(1L)).thenReturn(List.of(existing));

        assertThrows(DuplicateTicketException.class, () -> ticketService.addTicket(ticket("vpn DOWN")));
    }

    @Test
    void resolvingWithoutSummaryIsRejected() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        Ticket request = ticket("VPN down");
        request.setStatus("Resolved");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ticketService.updateTicket(10L, request));
        assertEquals("Please provide resolution details before marking resolve.", ex.getMessage());
    }

    @Test
    void resolvingWithSummarySetsResolutionDate() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(call -> call.getArgument(0));

        Ticket request = ticket("VPN down");
        request.setStatus("Resolved");
        request.setResolutionSummary("Restarted the gateway");
        request.setSatisfied(true);
        Ticket saved = ticketService.updateTicket(10L, request);

        assertEquals("Resolved", saved.getStatus());
        assertNotNull(saved.getResolutionDate());
        assertEquals(Boolean.TRUE, saved.getSatisfied());
    }

    @Test
    void onlyResolvedTicketCanBeClosed() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        Ticket request = ticket("VPN down");
        request.setStatus("Closed");

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateTicket(10L, request));
    }

    @Test
    void unavailableAgentCannotBeAssigned() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));
        when(supportAgentRepo.findById(5L)).thenReturn(Optional.of(agent(5L, "Unavailable")));

        Ticket request = ticket("VPN down");
        request.setSupportAgent(agent(5L, null));

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateTicket(10L, request));
    }

    @Test
    void assignedTicketCannotBeDeleted() {
        Ticket existing = ticket("VPN down");
        existing.setTicketId(10L);
        existing.setSupportAgent(agent(5L, "Available"));
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existing));

        assertThrows(TicketDeletionException.class, () -> ticketService.deleteTicket(10L));
        verify(ticketRepo, never()).delete(any(Ticket.class));
    }
}
