package com.examly.springapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import io.jsonwebtoken.Claims;

class JwtUtilsTest {

    private static final String SECRET = "VGVzdE9ubHlTZWNyZXRGb3JTdXBwb3J0U3BoZXJlSnVuaXRUZXN0czAxMjM0NTY3ODk=";

    private final JwtUtils jwtUtils = new JwtUtils(SECRET, 60_000);
    private final UserPrinciple principle = new UserPrinciple(7L, "client@test.com", "hash", "client", "Client");

    @Test
    void generatesTokenContainingIdentityClaims() {
        Claims claims = jwtUtils.validateToken(jwtUtils.generateToken(principle));
        assertThat(claims.getSubject()).isEqualTo("client@test.com");
        assertThat(claims.get(JwtUtils.CLAIM_USER_ID, Long.class)).isEqualTo(7L);
        assertThat(claims.get(JwtUtils.CLAIM_ROLE, String.class)).isEqualTo("Client");
        assertThat(jwtUtils.isTokenValidFor(claims, principle)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        long now = System.currentTimeMillis();
        String expired = jwtUtils.generateTokenWithExpiry(principle, new Date(now - 120_000), new Date(now - 60_000));
        assertThatThrownBy(() -> jwtUtils.validateToken(expired)).isInstanceOf(CredentialsExpiredException.class);
    }

    @Test
    void rejectsTamperedToken() {
        assertThatThrownBy(() -> jwtUtils.validateToken("not.a.jwt")).isInstanceOf(BadCredentialsException.class);
    }
}
