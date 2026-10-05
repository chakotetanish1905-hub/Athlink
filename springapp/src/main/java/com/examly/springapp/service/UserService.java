package com.examly.springapp.service;

import com.examly.springapp.model.LoginDTO;
import com.examly.springapp.model.LoginRequestDTO;
import com.examly.springapp.model.UserRequestDTO;
import com.examly.springapp.model.UserResponseDTO;

public interface UserService {

    /** Registers a new user: duplicate-email check, BCrypt encoding, persist. */
    UserResponseDTO createUser(UserRequestDTO user);

    /** Authenticates through AuthenticationManager -> DaoAuthenticationProvider and issues a JWT. */
    LoginDTO loginUser(LoginRequestDTO user);
}
