package com.examly.springapp.service;

import java.util.List;

import com.examly.springapp.model.TicketRequestDTO;
import com.examly.springapp.model.TicketResponseDTO;

public interface TicketService {

    TicketResponseDTO addTicket(TicketRequestDTO ticket);

    TicketResponseDTO getTicketById(Long ticketId);

    List<TicketResponseDTO> getAllTickets();

    TicketResponseDTO updateTicket(Long ticketId, TicketRequestDTO ticket);

    TicketResponseDTO deleteTicket(Long ticketId);

    List<TicketResponseDTO> getTicketsByAgentId(Long agentId);

    List<TicketResponseDTO> getTicketsByUserId(Long userId);
}
