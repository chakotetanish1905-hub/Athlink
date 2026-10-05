package com.examly.springapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.examly.springapp.config.JwtUtils;
import com.examly.springapp.config.UserPrinciple;
import com.examly.springapp.exceptions.DuplicateResourceException;
import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.LoginRequestDTO;
import com.examly.springapp.model.User;
import com.examly.springapp.model.UserRequestDTO;
import com.examly.springapp.model.UserResponseDTO;
import com.examly.springapp.repository.UserRepo;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

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

    /**
     * Registration flow: validated DTO -> duplicate email check -> BCrypt encode -> save.
     */
    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        LOGGER.debug("Registration requested for role={}", request.getUserRole());

        if (userRepo.existsByEmailIgnoreCase(email)) {
            LOGGER.debug("Registration rejected: duplicate email");
            throw new DuplicateResourceException("A user with this email already exists");
        }

        User user = new User(email,
                passwordEncoder.encode(request.getPassword()),
                request.getUsername().trim(),
                request.getMobileNumber().trim(),
                request.getUserRole());

        User saved = userRepo.save(user);
        LOGGER.info("User registered: userId={} role={}", saved.getUserId(), saved.getUserRole());
        return UserResponseDTO.from(saved);
    }

    /**
     * Login flow (DAO authentication):
     * UsernamePasswordAuthenticationToken -> AuthenticationManager -> DaoAuthenticationProvider
     * -> MyUserDetailsService -> UserRepo -> PasswordEncoder.matches() -> JWT.
     * Bad credentials raise AuthenticationException, which is mapped to 401.
     */
    @Override
    public LoginDTO loginUser(LoginRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        LOGGER.trace("Building UsernamePasswordAuthenticationToken for login");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        UserPrinciple principle = (UserPrinciple) authentication.getPrincipal();
        String token = jwtUtils.generateToken(principle);

        LOGGER.info("Login successful: userId={} role={}", principle.getUserId(), principle.getRole());
        return new LoginDTO(token, principle.getDisplayName(), principle.getRole(), principle.getUserId());
    }
}
