package com.examly.springapp.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request JWT authentication:
 * Authorization: Bearer &lt;jwt&gt; -> JwtUtils.validateToken -> MyUserDetailsService -> SecurityContext.
 * On an invalid/expired token the context stays empty and the reason is stored on the request,
 * so JwtAuthenticationEntryPoint can answer 401 with a precise message for protected endpoints.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    public static final String JWT_ERROR_ATTRIBUTE = "supportsphere.jwt.error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final MyUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, MyUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            LOGGER.trace("No bearer token on {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtUtils.validateToken(token);
            String email = jwtUtils.extractUsername(claims);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (jwtUtils.isTokenValidFor(claims, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                    LOGGER.trace("Authenticated request for authorities {}", userDetails.getAuthorities());
                }
            }
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute(JWT_ERROR_ATTRIBUTE, ex.getMessage());
            LOGGER.debug("JWT authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        }

        chain.doFilter(request, response);
    }
}
