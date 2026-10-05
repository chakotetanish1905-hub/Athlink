package com.examly.springapp.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.service.ErrorLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 403 Forbidden when an authenticated user lacks the role required by SecurityConfig.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper, ErrorLogService errorLogService) {
        this.objectMapper = objectMapper;
        this.errorLogService = errorLogService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        String message = "You are not authorized to access this resource.";
        LOGGER.warn("403 Forbidden: {} {}", request.getMethod(), request.getRequestURI());
        errorLogService.logError(HttpStatus.FORBIDDEN.value(), message, request.getRequestURI(),
                accessDeniedException.getClass().getSimpleName());

        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
