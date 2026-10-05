package com.examly.springapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.examly.springapp.model.User;

class JwtUtilsTest {

    private static final String SECRET = "VGVzdE9ubHlTZWNyZXRGb3JTdXBwb3J0U3BoZXJlSnVuaXRUZXN0czAxMjM0NTY3ODk=";

    private UserPrinciple sampleUser() {
        User user = new User();
        user.setUserId(7L);
        user.setEmail("alice@test.com");
        user.setPassword("encoded");
        user.setUsername("Alice");
        user.setUserRole("Client");
        return new UserPrinciple(user);
    }

    @Test
    void generatedTokenIsValidAndContainsTheEmail() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 60000);
        String token = jwtUtils.generateToken(sampleUser());

        assertTrue(jwtUtils.validateToken(token));
        assertEquals("alice@test.com", jwtUtils.getEmailFromToken(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, -1000);
        String token = jwtUtils.generateToken(sampleUser());

        assertFalse(jwtUtils.validateToken(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 60000);
        String token = jwtUtils.generateToken(sampleUser()) + "x";

        assertFalse(jwtUtils.validateToken(token));
        assertFalse(jwtUtils.validateToken("not-a-token"));
    }

    @Test
    void userPrincipleMapsRoleToAuthority() {
        UserPrinciple user = sampleUser();
        assertEquals("ROLE_CLIENT", user.getAuthorities().iterator().next().getAuthority());
        assertTrue(user.isClient());
    }
}
