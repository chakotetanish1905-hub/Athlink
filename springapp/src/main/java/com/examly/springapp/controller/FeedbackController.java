package com.examly.springapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.Feedback;
import com.examly.springapp.model.User;
import com.examly.springapp.service.FeedbackService;

import jakarta.validation.Valid;

// URL role rules are in SecurityConfig. A Client can only see and delete their own feedback.
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    // Client: 201 with the new feedback
    @PostMapping
    public ResponseEntity<Feedback> createFeedback(@Valid @RequestBody Feedback feedback,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        // The feedback always belongs to the logged-in client
        User owner = new User();
        owner.setUserId(currentUser.getUserId());
        feedback.setUser(owner);

        Feedback savedFeedback = feedbackService.createFeedback(feedback);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFeedback);
    }

    // Manager: any feedback. Client: only their own.
    @GetMapping("/{feedbackId}")
    public ResponseEntity<Feedback> getFeedbackById(@PathVariable Long feedbackId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        if (currentUser.isClient()) {
            checkOwner(feedback, currentUser);
        }
        return ResponseEntity.ok(feedback);
    }

    // Manager: all feedback. Client: only their own. 204 when there is none.
    @GetMapping
    public ResponseEntity<List<Feedback>> getAllFeedbacks(@AuthenticationPrincipal UserPrinciple currentUser) {
        List<Feedback> feedbacks;
        if (currentUser != null && currentUser.isClient()) {
            feedbacks = feedbackService.getFeedbacksByUserId(currentUser.getUserId());
        } else {
            feedbacks = feedbackService.getAllFeedbacks();
        }

        if (feedbacks.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    // Client: their own feedback only
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Feedback>> getFeedbacksByUserId(@PathVariable Long userId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        if (!userId.equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only view your own feedback");
        }

        List<Feedback> feedbacks = feedbackService.getFeedbacksByUserId(userId);
        if (feedbacks.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(feedbacks);
    }

    // Client: 200 with the deleted feedback
    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<Feedback> deleteFeedback(@PathVariable Long feedbackId,
            @AuthenticationPrincipal UserPrinciple currentUser) {
        Feedback feedback = feedbackService.getFeedbackById(feedbackId);
        checkOwner(feedback, currentUser);

        Feedback deletedFeedback = feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.ok(deletedFeedback);
    }

    // Throws 403 when a client tries to use somebody else's feedback.
    private void checkOwner(Feedback feedback, UserPrinciple currentUser) {
        if (!feedback.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You can only access your own feedback");
        }
    }
}
