package com.examly.springapp.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.service.ErrorLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 401 Unauthorized when an unauthenticated request (no / invalid / expired JWT)
 * reaches a protected endpoint. The request never reaches the controller.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper, ErrorLogService errorLogService) {
        this.objectMapper = objectMapper;
        this.errorLogService = errorLogService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        Object jwtError = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);
        String message = jwtError != null ? jwtError.toString()
                : "Authentication is required to access this resource.";

        LOGGER.warn("401 Unauthorized: {} {} - {}", request.getMethod(), request.getRequestURI(), message);
        errorLogService.logError(HttpStatus.UNAUTHORIZED.value(), message, request.getRequestURI(),
                authException.getClass().getSimpleName());

        ErrorResponseDTO body = new ErrorResponseDTO(HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
