package com.examly.springapp.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Sends 401 Unauthorized when a protected URL is called without a valid JWT.
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorLogRepo errorLogRepo;

    public JwtAuthenticationEntryPoint(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = "Please login to access this resource";
        errorLogRepo.save(new ErrorLog(401, message, request.getRequestURI(), "Unauthorized"));

        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
