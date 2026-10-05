package com.examly.springapp.service;

import java.util.List;

import com.examly.springapp.model.FeedbackRequestDTO;
import com.examly.springapp.model.FeedbackResponseDTO;

public interface FeedbackService {

    FeedbackResponseDTO createFeedback(FeedbackRequestDTO feedback);

    FeedbackResponseDTO getFeedbackById(Long feedbackId);

    List<FeedbackResponseDTO> getAllFeedbacks();

    FeedbackResponseDTO deleteFeedback(Long feedbackId);

    List<FeedbackResponseDTO> getFeedbacksByUserId(Long userId);
}
