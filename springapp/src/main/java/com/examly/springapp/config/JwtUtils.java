package com.examly.springapp.config;

import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// Creates and validates JSON Web Tokens.
@Component
public class JwtUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtils.class);

    private final Key key;
    private final long expirationMs;

    public JwtUtils(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    // Called after a successful login. The email is the token subject.
    public String generateToken(UserPrinciple user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getUserId())
                .claim("role", user.getUserRole())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Returns the email stored in the token.
    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    // A token is valid when the signature is correct and it has not expired.
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            LOGGER.debug("JWT rejected: token has expired");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // Only the reason is logged - never the token itself
            LOGGER.debug("JWT rejected: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
