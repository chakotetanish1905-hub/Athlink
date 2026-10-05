package com.examly.springapp.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.examly.springapp.model.User;

// The logged-in user as Spring Security sees it.
// The email is used as the "username" for login, and the role "Manager"/"Client"
// becomes the authority "ROLE_MANAGER"/"ROLE_CLIENT".
public class UserPrinciple implements UserDetails {

    private Long userId;
    private String email;
    private String password;
    private String username;
    private String userRole;
    private List<GrantedAuthority> authorities;

    public UserPrinciple(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.username = user.getUsername();
        this.userRole = user.getUserRole();
        this.authorities = new ArrayList<>();
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().toUpperCase()));
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    // The display name of the user (for example "Rahul Verma").
    public String getDisplayName() {
        return username;
    }

    public String getUserRole() {
        return userRole;
    }

    public boolean isManager() {
        return "Manager".equals(userRole);
    }

    public boolean isClient() {
        return "Client".equals(userRole);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // Spring Security calls this the username; in SupportSphere users log in with their email.
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
