package com.examly.springapp.service;

import java.util.List;
import java.util.Optional;

import com.examly.springapp.model.Ticket;

public interface TicketService {

    Ticket addTicket(Ticket ticket);

    Optional<Ticket> getTicketById(Long ticketId);

    List<Ticket> getAllTickets();

    Ticket updateTicket(Long ticketId, Ticket ticket);

    Ticket deleteTicket(Long ticketId);

    List<Ticket> getTicketsByAgentId(Long agentId);

    List<Ticket> getTicketsByUserId(Long userId);
}
