package com.examly.springapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.exceptions.DuplicateTicketException;
import com.examly.springapp.exceptions.ForbiddenOperationException;
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
    @Mock
    private CurrentUserService currentUserService;

    private TicketServiceImpl ticketService;
    private User client;
    private UserPrinciple clientPrinciple;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketRepo, userRepo, supportAgentRepo, feedbackRepo,
                currentUserService);
        client = new User(1L, "c@test.com", "hash", "client", "9876543210", "Client");
        clientPrinciple = UserPrinciple.build(client);
    }

    private Ticket existingTicket() {
        Ticket ticket = new Ticket("Printer", "Printer not working", "High", "Technical", client);
        ticket.setTicketId(10L);
        ticket.setCreatedDate(LocalDate.now());
        return ticket;
    }

    @Test
    void addTicketStartsOpenForAuthenticatedClient() {
        when(currentUserService.requireCurrentUserId()).thenReturn(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        when(ticketRepo.existsByUserUserIdAndTitleIgnoreCase(1L, "Printer")).thenReturn(false);
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketRequestDTO request = new TicketRequestDTO("Printer", "Printer not working", "High", "Technical");
        request.setStatus("Closed"); // ignored: new tickets always start Open
        TicketResponseDTO response = ticketService.addTicket(request);

        assertThat(response.getStatus()).isEqualTo("Open");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getCreatedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void addTicketRejectsDuplicateTitle() {
        when(currentUserService.requireCurrentUserId()).thenReturn(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(client));
        when(ticketRepo.existsByUserUserIdAndTitleIgnoreCase(1L, "Printer")).thenReturn(true);

        TicketRequestDTO request = new TicketRequestDTO("Printer", "desc", "High", "Technical");
        assertThatThrownBy(() -> ticketService.addTicket(request)).isInstanceOf(DuplicateTicketException.class);
    }

    @Test
    void addTicketRejectsForeignUserId() {
        when(currentUserService.requireCurrentUserId()).thenReturn(1L);
        TicketRequestDTO request = new TicketRequestDTO("Printer", "desc", "High", "Technical");
        request.setUserId(99L);
        assertThatThrownBy(() -> ticketService.addTicket(request)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void resolvingWithoutSummaryIsRejected() {
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existingTicket()));
        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(clientPrinciple));

        TicketRequestDTO request = new TicketRequestDTO("Printer", "Printer not working", "High", "Technical");
        request.setStatus("Resolved");
        assertThatThrownBy(() -> ticketService.updateTicket(10L, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("resolution");
    }

    @Test
    void resolvingWithSummarySetsResolutionDate() {
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(existingTicket()));
        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(clientPrinciple));
        when(ticketRepo.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketRequestDTO request = new TicketRequestDTO("Printer", "Printer not working", "High", "Technical");
        request.setStatus("Resolved");
        request.setResolutionSummary("Replaced toner");
        request.setSatisfied(true);
        TicketResponseDTO response = ticketService.updateTicket(10L, request);

        assertThat(response.getStatus()).isEqualTo("Resolved");
        assertThat(response.getResolutionDate()).isEqualTo(LocalDate.now());
        assertThat(response.getSatisfied()).isTrue();
    }

    @Test
    void clientCannotCloseTicket() {
        Ticket ticket = existingTicket();
        ticket.setStatus("Resolved");
        ticket.setResolutionSummary("done");
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(clientPrinciple));

        TicketRequestDTO request = new TicketRequestDTO("Printer", "Printer not working", "High", "Technical");
        request.setStatus("Closed");
        assertThatThrownBy(() -> ticketService.updateTicket(10L, request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void assignedTicketCannotBeDeleted() {
        Ticket ticket = existingTicket();
        SupportAgent agent = new SupportAgent("Agent", "a@test.com", "9876543211", "Technical", "3 years",
                "Available");
        agent.setAgentId(3L);
        ticket.setSupportAgent(agent);
        when(ticketRepo.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.deleteTicket(10L)).isInstanceOf(TicketDeletionException.class);
    }
}
