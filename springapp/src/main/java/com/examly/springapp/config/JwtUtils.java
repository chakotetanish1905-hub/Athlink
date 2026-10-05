package com.examly.springapp.config;

import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * All JWT concerns in one place: generation, parsing, validation and expiry checks.
 * Secret and expiry come from configuration (jwt.secret / jwt.expiration), never from source code.
 */
@Component
public class JwtUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtils.class);
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtils(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserPrinciple principle) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(principle.getUsername())
                .claim(CLAIM_USER_ID, principle.getUserId())
                .claim(CLAIM_ROLE, principle.getRole())
                .claim(CLAIM_USERNAME, principle.getDisplayName())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses and verifies signature + expiry.
     * @throws CredentialsExpiredException when the token has expired (mapped to 401)
     * @throws BadCredentialsException when the token is malformed or the signature is wrong (401)
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException ex) {
            LOGGER.debug("JWT rejected: expired");
            throw new CredentialsExpiredException("JWT token has expired. Please login again.");
        } catch (JwtException | IllegalArgumentException ex) {
            LOGGER.debug("JWT rejected: {}", ex.getClass().getSimpleName());
            throw new BadCredentialsException("Invalid JWT token.");
        }
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration() == null || claims.getExpiration().before(new Date());
    }

    /** Token belongs to this user and has not expired. */
    public boolean isTokenValidFor(Claims claims, UserDetails userDetails) {
        return userDetails.getUsername().equals(extractUsername(claims)) && !isTokenExpired(claims);
    }

    /** Test/utility hook: builds a token with an explicit expiry (used to verify expired-token handling). */
    public String generateTokenWithExpiry(UserPrinciple principle, Date issuedAt, Date expiresAt) {
        return Jwts.builder()
                .setSubject(principle.getUsername())
                .claim(CLAIM_USER_ID, principle.getUserId())
                .claim(CLAIM_ROLE, principle.getRole())
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
