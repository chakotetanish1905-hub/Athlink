package com.examly.springapp.model;

/**
 * Public view of a User. The password hash is never exposed.
 */
public class UserResponseDTO {

    private Long userId;
    private String email;
    private String username;
    private String mobileNumber;
    private String userRole;

    public UserResponseDTO() {
    }

    public UserResponseDTO(Long userId, String email, String username, String mobileNumber, String userRole) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.mobileNumber = mobileNumber;
        this.userRole = userRole;
    }

    public static UserResponseDTO from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponseDTO(user.getUserId(), user.getEmail(), user.getUsername(),
                user.getMobileNumber(), user.getUserRole());
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
}
