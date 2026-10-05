package com.examly.springapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.examly.springapp.model.User;

public interface UserRepo extends JpaRepository<User, Long> {

    // Used by login (MyUserDetailsService) and by the duplicate-email check on register.
    User findByEmail(String email);
}
