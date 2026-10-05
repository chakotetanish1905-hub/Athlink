package com.examly.springapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.examly.springapp.repository.UserRepo;

/**
 * Loads users for DaoAuthenticationProvider (login) and JwtAuthenticationFilter (each request).
 * It only LOADS the user; password verification is done by DaoAuthenticationProvider
 * with PasswordEncoder.matches().
 */
@Service
public class MyUserDetailsService implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyUserDetailsService.class);

    private final UserRepo userRepo;

    public MyUserDetailsService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    /** @param username the user's email address (the authentication identity) */
    @Override
    public UserDetails loadUserByUsername(String username) {
        LOGGER.trace("Loading user details from database");
        return userRepo.findByEmail(username == null ? null : username.trim().toLowerCase())
                .map(UserPrinciple::build)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
