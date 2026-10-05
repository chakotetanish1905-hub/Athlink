package com.examly.springapp.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration request for POST /api/register.
 */
public class UserRequestDTO {

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 100, message = "Email must not exceed 100 characters.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 64, message = "Password must contain at least 8 characters.")
    private String password;

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
    private String username;

    @NotBlank(message = "Mobile number is required.")
    @Pattern(regexp = "^\\d{10}$", message = "Mobile number must contain exactly 10 digits.")
    private String mobileNumber;

    @NotBlank(message = "User role is required.")
    @Pattern(regexp = "^(Manager|Client)$", message = "User role must be either Manager or Client.")
    private String userRole;

    public UserRequestDTO() {
    }

    public UserRequestDTO(String email, String password, String username, String mobileNumber, String userRole) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.mobileNumber = mobileNumber;
        this.userRole = userRole;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
