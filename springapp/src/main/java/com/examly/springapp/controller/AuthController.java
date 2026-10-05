package com.examly.springapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.model.ErrorResponseDTO;
import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.LoginRequestDTO;
import com.examly.springapp.model.UserRequestDTO;
import com.examly.springapp.model.UserResponseDTO;
import com.examly.springapp.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Authentication", description = "Registration and login (public endpoints)")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register a user",
            description = "Creates a Manager or Client account. The password is stored as a BCrypt hash.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "email, password (min 8 chars), username, mobileNumber (10 digits), userRole (Manager|Client)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered",
                    content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "A user with this email already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<UserResponseDTO> registerUser(@Valid @RequestBody UserRequestDTO request) {
        LOGGER.debug("POST /api/register");
        UserResponseDTO created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Login",
            description = "Authenticates through AuthenticationManager -> DaoAuthenticationProvider -> "
                    + "MyUserDetailsService -> PasswordEncoder and returns a JWT with username, userRole and userId.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "email and password")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))) })
    public ResponseEntity<LoginDTO> loginUser(@Valid @RequestBody LoginRequestDTO request) {
        LOGGER.debug("POST /api/login");
        LoginDTO login = userService.loginUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(login);
    }
}
