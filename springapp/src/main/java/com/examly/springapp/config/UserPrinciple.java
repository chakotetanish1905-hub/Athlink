package com.examly.springapp.config;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.examly.springapp.model.User;

/**
 * Spring Security view of a SupportSphere user.
 * getUsername() returns the email (the authentication identity); the display name is kept separately.
 * Database role "Manager"/"Client" becomes authority "ROLE_MANAGER"/"ROLE_CLIENT".
 */
public class UserPrinciple implements UserDetails {

    private static final long serialVersionUID = 1L;
    public static final String ROLE_MANAGER = "Manager";
    public static final String ROLE_CLIENT = "Client";

    private final Long userId;
    private final String email;
    private final String password;
    private final String displayName;
    private final String role;
    private final List<GrantedAuthority> authorities;

    public UserPrinciple(Long userId, String email, String password, String displayName, String role) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    public static UserPrinciple build(User user) {
        return new UserPrinciple(user.getUserId(), user.getEmail(), user.getPassword(), user.getUsername(),
                user.getUserRole());
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public boolean isManager() {
        return ROLE_MANAGER.equalsIgnoreCase(role);
    }

    public boolean isClient() {
        return ROLE_CLIENT.equalsIgnoreCase(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

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
