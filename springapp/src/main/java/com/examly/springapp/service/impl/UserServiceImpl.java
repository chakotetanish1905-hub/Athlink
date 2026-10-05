package com.examly.springapp.service.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import com.examly.springapp.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public UserServiceImpl(UserRepo userRepo, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtUtils jwtUtils) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public User createUser(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // Check whether the email already exists (answered with 409 by GlobalExceptionHandler)
        if (userRepo.findByEmail(email) != null) {
            throw new IllegalStateException("A user with this email already exists");
        }

        user.setUserId(null);
        user.setEmail(email);
        user.setUsername(user.getUsername().trim());
        // Never store the plain password: save the BCrypt hash instead
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepo.save(user);
    }

    @Override
    public LoginDTO loginUser(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // AuthenticationManager -> DaoAuthenticationProvider -> MyUserDetailsService -> PasswordEncoder.matches()
        // Wrong email or password throws BadCredentialsException (answered with 401).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, user.getPassword()));

        UserPrinciple principle = (UserPrinciple) authentication.getPrincipal();
        String token = jwtUtils.generateToken(principle);

        return new LoginDTO(token, principle.getDisplayName(), principle.getUserRole(), principle.getUserId());
    }
}
