package com.examly.springapp.exceptions;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;

// Turns exceptions into friendly JSON error responses and saves every error in the ErrorLogs table.
//
//   400 Bad Request  - validation errors, IllegalArgumentException (a business rule was broken)
//   401 Unauthorized - wrong email or password
//   403 Forbidden    - AccessDeniedException (for example, another client's ticket)
//   404 Not Found    - NoSuchElementException
//   409 Conflict     - duplicate data or a delete that is not allowed
//   500              - anything unexpected
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogRepo errorLogRepo;

    public GlobalExceptionHandler(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        // Collect one message per invalid field, for example {"title": "Title is required"}
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String firstMessage = "Please fill in all required fields correctly.";
        if (!fieldErrors.isEmpty()) {
            firstMessage = fieldErrors.values().iterator().next();
        }

        ResponseEntity<Map<String, Object>> response = buildResponse(HttpStatus.BAD_REQUEST, firstMessage,
                request, ex);
        response.getBody().put("errors", fieldErrors);
        return response;
    }

    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (ex instanceof HttpMessageNotReadableException) {
            message = unreadableBodyMessage(ex);
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, ex);
    }

    // An unknown enum value in the JSON (for example "status": "Pending") is reported with the enum's own
    // message, e.g. "Status must be Open, In Progress, Resolved or Closed". Anything else is a generic message.
    private String unreadableBodyMessage(Exception ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return "The request body is not valid";
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleLoginFailure(AuthenticationException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, ex);
    }

    @ExceptionHandler({ DuplicateAgentException.class, DuplicateTicketException.class,
            AgentDeletionException.class, TicketDeletionException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "This data conflicts with an existing record", request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOtherErrors(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.",
                request, ex);
    }

    // Saves the error in the ErrorLogs table and builds the JSON body sent to Angular.
    // Expected client errors (4xx) are logged once here at WARN, without a stack trace.
    // Unexpected errors (500) are logged at ERROR with the stack trace in handleOtherErrors.
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message,
            HttpServletRequest request, Exception ex) {
        if (status.is4xxClientError()) {
            LOGGER.warn("{} {} on {} {}: {}", status.value(), ex.getClass().getSimpleName(),
                    request.getMethod(), request.getRequestURI(), message);
        }

        try {
            errorLogRepo.save(new ErrorLog(status.value(), message, request.getRequestURI(),
                    ex.getClass().getSimpleName()));
        } catch (RuntimeException saveError) {
            // Still answer the client even if the ErrorLogs table cannot be written (for example, database down)
            LOGGER.error("Could not save the error in ErrorLogs", saveError);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
