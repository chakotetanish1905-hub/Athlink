package com.examly.springapp.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Gives every HTTP request a short request id so all log lines of one request can be found together.
// The id is taken from the "X-Request-Id" header when the caller sends a safe one, otherwise generated.
// It is put in the logging MDC (shown as requestId=... in every log line) and returned in the response header.
// Runs first, before Spring Security, so security log lines carry the id too.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // Only short letters/digits/dashes are accepted, so a caller cannot inject text into the logs
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // The thread goes back to the pool: never leak the id into the next request
            MDC.remove(MDC_KEY);
        }
    }
}
