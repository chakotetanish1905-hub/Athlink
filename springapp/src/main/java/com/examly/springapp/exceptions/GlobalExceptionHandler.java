package com.examly.springapp.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.service.ErrorLogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Converts every exception into a consistent ErrorResponseDTO with the correct HTTP status,
 * records it in the ErrorLogs table and never exposes stack traces to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogService errorLogService;

    public GlobalExceptionHandler(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    /** ResourceNotFound (404), Duplicate* (409), AgentDeletion/TicketDeletion (409), Validation (400), Forbidden (403). */
    @ExceptionHandler(SupportSphereException.class)
    public ResponseEntity<ErrorResponseDTO> handleSupportSphere(SupportSphereException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request, ex, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed. Please correct the highlighted fields.", request,
                ex, errors);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponseDTO> handleUnreadable(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request. Please check the submitted values.", request, ex,
                null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data.", request, ex, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex,
            HttpServletRequest request) {
        String message = ex instanceof BadCredentialsException ? "Invalid email or password"
                : "Authentication failed. Please login again.";
        return build(HttpStatus.UNAUTHORIZED, message, request, ex, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You are not authorized to access this resource.", request, ex, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, ex, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.", request, ex,
                null);
    }

    private ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String message, HttpServletRequest request,
            Exception ex, Map<String, String> validationErrors) {
        String path = request.getRequestURI();
        if (status.is4xxClientError()) {
            LOGGER.warn("{} {} on {} {}: {}", status.value(), ex.getClass().getSimpleName(), request.getMethod(), path,
                    message);
        }
        errorLogService.logError(status.value(), message, path, ex.getClass().getSimpleName());
        ErrorResponseDTO body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, path,
                validationErrors);
        return ResponseEntity.status(status).body(body);
    }
}
