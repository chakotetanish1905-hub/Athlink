package com.examly.springapp.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs once for every request.
// If the request has "Authorization: Bearer <token>" and the token is valid,
// the user is loaded and stored in the SecurityContext for this request only.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;
    private final MyUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, MyUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.getEmailFromToken(token);
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // Create an authenticated Authentication and put it in the SecurityContext
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (UsernameNotFoundException e) {
                    // The token is valid but its user was deleted: continue without authentication (401)
                    LOGGER.warn("Valid JWT for a user that no longer exists on {} {}",
                            request.getMethod(), request.getRequestURI());
                    SecurityContextHolder.clearContext();
                }
            }
        }

        // Missing or invalid token: no authentication is set, so protected URLs answer 401.
        filterChain.doFilter(request, response);
    }
}
