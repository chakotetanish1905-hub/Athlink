package com.examly.springapp.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;

// Step 5 of the DAO authentication flow: load the user from the database.
// This class only LOADS the user. DaoAuthenticationProvider checks the password
// with PasswordEncoder.matches().
@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    public MyUserDetailsService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // DaoAuthenticationProvider turns UsernameNotFoundException into "bad credentials" (401)
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return new UserPrinciple(user);
    }
}
