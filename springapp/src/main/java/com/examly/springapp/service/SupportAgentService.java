package com.examly.springapp.service;

import java.util.List;

import com.examly.springapp.model.SupportAgentRequestDTO;
import com.examly.springapp.model.SupportAgentResponseDTO;

public interface SupportAgentService {

    SupportAgentResponseDTO addSupportAgent(SupportAgentRequestDTO supportAgent);

    SupportAgentResponseDTO getSupportAgentById(Long agentId);

    List<SupportAgentResponseDTO> getAllSupportAgents();

    SupportAgentResponseDTO updateSupportAgent(Long agentId, SupportAgentRequestDTO supportAgent);

    SupportAgentResponseDTO deleteSupportAgent(Long agentId);
}
