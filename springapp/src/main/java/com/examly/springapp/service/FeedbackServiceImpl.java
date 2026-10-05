package com.examly.springapp.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.exceptions.DuplicateResourceException;
import com.examly.springapp.exceptions.ForbiddenOperationException;
import com.examly.springapp.exceptions.ResourceNotFoundException;
import com.examly.springapp.exceptions.ValidationException;
import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.FeedbackRequestDTO;
import com.examly.springapp.model.FeedbackResponseDTO;
import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.model.Ticket;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.FeedbackRepo;
import com.examly.springapp.repository.SupportAgentRepo;
import com.examly.springapp.repository.TicketRepo;
import com.examly.springapp.repository.UserRepo;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private final FeedbackRepo feedbackRepo;
    private final TicketRepo ticketRepo;
    private final UserRepo userRepo;
    private final SupportAgentRepo supportAgentRepo;
    private final CurrentUserService currentUserService;

    public FeedbackServiceImpl(FeedbackRepo feedbackRepo, TicketRepo ticketRepo, UserRepo userRepo,
            SupportAgentRepo supportAgentRepo, CurrentUserService currentUserService) {
        this.feedbackRepo = feedbackRepo;
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.supportAgentRepo = supportAgentRepo;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public FeedbackResponseDTO createFeedback(FeedbackRequestDTO request) {
        Long currentUserId = currentUserService.requireCurrentUserId();
        Long requestedUserId = request.resolveUserId();
        if (requestedUserId != null && !Objects.equals(requestedUserId, currentUserId)) {
            throw new ForbiddenOperationException("You can only post feedback from your own account.");
        }

        Long ticketId = request.resolveTicketId();
        if (ticketId == null) {
            throw new ValidationException("Ticket is required.");
        }
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));
        if (!Objects.equals(ticket.getUser().getUserId(), currentUserId)) {
            throw new ForbiddenOperationException("You can only review your own tickets.");
        }
        if (!Ticket.STATUS_RESOLVED.equals(ticket.getStatus()) && !Ticket.STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new ValidationException("Feedback can only be given for Resolved or Closed tickets.");
        }
        if (feedbackRepo.existsByUserUserIdAndTicketTicketId(currentUserId, ticketId)) {
            throw new DuplicateResourceException("Feedback for this ticket already exists");
        }

        User user = userRepo.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));
        SupportAgent agent = resolveAgent(ticket, request.resolveAgentId());

        Feedback feedback = new Feedback(request.getFeedbackText().trim(),
                request.getDate() != null ? request.getDate() : LocalDate.now(),
                request.getCategory().trim(), request.getRating(), user, agent, ticket);

        Feedback saved = feedbackRepo.save(feedback);
        LOGGER.info("Feedback created: feedbackId={} ticketId={} rating={}", saved.getFeedbackId(), ticketId,
                saved.getRating());
        return FeedbackResponseDTO.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponseDTO getFeedbackById(Long feedbackId) {
        Feedback feedback = findFeedback(feedbackId);
        currentUserService.assertOwnerOrManager(feedback.getUser().getUserId());
        return FeedbackResponseDTO.from(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getAllFeedbacks() {
        List<Feedback> feedbacks = currentUserService.getCurrentUser()
                .filter(principle -> !principle.isManager())
                .map(principle -> feedbackRepo.findByUserUserId(principle.getUserId()))
                .orElseGet(feedbackRepo::findAll);
        LOGGER.debug("getAllFeedbacks returned {} feedbacks", feedbacks.size());
        return feedbacks.stream().map(FeedbackResponseDTO::from).toList();
    }

    @Override
    @Transactional
    public FeedbackResponseDTO deleteFeedback(Long feedbackId) {
        Feedback feedback = findFeedback(feedbackId);
        currentUserService.assertOwnerOrManager(feedback.getUser().getUserId());
        FeedbackResponseDTO deleted = FeedbackResponseDTO.from(feedback);
        feedbackRepo.delete(feedback);
        LOGGER.info("Feedback deleted: feedbackId={}", feedbackId);
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getFeedbacksByUserId(Long userId) {
        currentUserService.assertOwnerOrManager(userId);
        if (!userRepo.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return feedbackRepo.findByUserUserId(userId).stream().map(FeedbackResponseDTO::from).toList();
    }

    private Feedback findFeedback(Long feedbackId) {
        return feedbackRepo.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + feedbackId));
    }

    /** The agent on the feedback is the one who worked the ticket; a mismatching agentId is rejected. */
    private SupportAgent resolveAgent(Ticket ticket, Long requestedAgentId) {
        SupportAgent ticketAgent = ticket.getSupportAgent();
        if (requestedAgentId == null) {
            return ticketAgent;
        }
        if (ticketAgent != null) {
            if (!Objects.equals(ticketAgent.getAgentId(), requestedAgentId)) {
                throw new ValidationException("The selected agent did not work on this ticket.");
            }
            return ticketAgent;
        }
        return supportAgentRepo.findById(requestedAgentId)
                .orElseThrow(() -> new ResourceNotFoundException("Support agent not found with id: " + requestedAgentId));
    }
}
