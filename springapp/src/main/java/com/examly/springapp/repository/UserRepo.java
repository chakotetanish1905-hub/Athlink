package com.examly.springapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.User;

public interface UserRepo extends JpaRepository<User, Long> {

    // Login: the user may not exist, so the caller decides what to do when it is empty
    Optional<User> findByEmail(String email);

    // Register: only needs to know whether the email is already taken
    boolean existsByEmail(String email);
}
