package com.examly.springapp.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.AgentDeletionException;
import com.examly.springapp.exceptions.DuplicateAgentException;
import com.examly.springapp.exceptions.ResourceNotFoundException;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.SupportAgentRequestDTO;
import com.examly.springapp.model.SupportAgentResponseDTO;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;

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
    public SupportAgentResponseDTO addSupportAgent(SupportAgentRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();
        if (supportAgentRepo.existsByEmailIgnoreCase(email)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }
        if (supportAgentRepo.existsByPhone(phone)) {
            throw new DuplicateAgentException("A support agent with this phone number already exists");
        }

        SupportAgent agent = new SupportAgent();
        copyFields(request, agent);
        agent.setEmail(email);
        agent.setPhone(phone);
        agent.setAddedDate(request.getAddedDate() != null ? request.getAddedDate() : LocalDate.now());

        SupportAgent saved = supportAgentRepo.save(agent);
        LOGGER.info("Support agent created: agentId={} expertise={}", saved.getAgentId(), saved.getExpertise());
        return SupportAgentResponseDTO.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportAgentResponseDTO getSupportAgentById(Long agentId) {
        return SupportAgentResponseDTO.from(findAgent(agentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportAgentResponseDTO> getAllSupportAgents() {
        List<SupportAgent> agents = supportAgentRepo.findAll();
        LOGGER.debug("getAllSupportAgents returned {} agents", agents.size());
        return agents.stream().map(SupportAgentResponseDTO::from).toList();
    }

    @Override
    @Transactional
    public SupportAgentResponseDTO updateSupportAgent(Long agentId, SupportAgentRequestDTO request) {
        SupportAgent agent = findAgent(agentId);
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();
        if (supportAgentRepo.existsByEmailIgnoreCaseAndAgentIdNot(email, agentId)) {
            throw new DuplicateAgentException("A support agent with this email already exists");
        }
        if (supportAgentRepo.existsByPhoneAndAgentIdNot(phone, agentId)) {
            throw new DuplicateAgentException("A support agent with this phone number already exists");
        }

        String existingProfile = agent.getProfile();
        LocalDate existingAddedDate = agent.getAddedDate();
        copyFields(request, agent);
        agent.setEmail(email);
        agent.setPhone(phone);
        // Keep stored values when the client omits them (e.g. status toggle without re-uploading profile).
        if (request.getProfile() == null) {
            agent.setProfile(existingProfile);
        }
        agent.setAddedDate(request.getAddedDate() != null ? request.getAddedDate() : existingAddedDate);

        SupportAgent saved = supportAgentRepo.save(agent);
        LOGGER.info("Support agent updated: agentId={} status={}", saved.getAgentId(), saved.getStatus());
        return SupportAgentResponseDTO.from(saved);
    }

    @Override
    @Transactional
    public SupportAgentResponseDTO deleteSupportAgent(Long agentId) {
        SupportAgent agent = findAgent(agentId);
        if (ticketRepo.existsBySupportAgentAgentId(agentId)) {
            throw new AgentDeletionException(
                    "Support agent is assigned to one or more tickets and cannot be deleted.");
        }
        if (feedbackRepo.existsBySupportAgentAgentId(agentId)) {
            throw new AgentDeletionException("Support agent has feedback records and cannot be deleted.");
        }
        SupportAgentResponseDTO deleted = SupportAgentResponseDTO.from(agent);
        supportAgentRepo.delete(agent);
        LOGGER.info("Support agent deleted: agentId={}", agentId);
        return deleted;
    }

    private SupportAgent findAgent(Long agentId) {
        return supportAgentRepo.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Support agent not found with id: " + agentId));
    }

    private void copyFields(SupportAgentRequestDTO request, SupportAgent agent) {
        agent.setName(request.getName().trim());
        agent.setExpertise(request.getExpertise().trim());
        agent.setExperience(request.getExperience().trim());
        agent.setStatus(request.getStatus());
        agent.setProfile(request.getProfile());
        agent.setShiftTiming(request.getShiftTiming().trim());
        agent.setRemarks(request.getRemarks());
    }
}
