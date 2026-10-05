package com.examly.springapp.model;

// Sent back to Angular after a successful login (SRS: LoginDTO).
public class LoginDTO {

    private String token;
    private String username;
    private String userRole;
    private Long userId;

    public LoginDTO() {
    }

    public LoginDTO(String token, String username, String userRole, Long userId) {
        this.token = token;
        this.username = username;
        this.userRole = userRole;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
