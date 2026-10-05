package com.examly.springapp.config;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.examly.springapp.model.ErrorLog;
import com.examly.springapp.repository.ErrorLogRepo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Sends 403 Forbidden when a logged-in user calls a URL that their role is not allowed to use.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorLogRepo errorLogRepo;

    public JwtAccessDeniedHandler(ErrorLogRepo errorLogRepo) {
        this.errorLogRepo = errorLogRepo;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        String message = "You are not allowed to access this resource";
        errorLogRepo.save(new ErrorLog(403, message, request.getRequestURI(), "Forbidden"));

        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"" + message + "\"}");
    }
}
