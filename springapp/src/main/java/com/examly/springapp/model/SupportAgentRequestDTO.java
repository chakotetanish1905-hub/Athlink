package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST/PUT /api/supportAgent. Field names mirror the SRS SupportAgent model.
 */
public class SupportAgentRequestDTO {

    @NotBlank(message = "Name is required.")
    @Size(max = 100, message = "Name must not exceed 100 characters.")
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Phone is required.")
    @Pattern(regexp = "^\\d{10}$", message = "Phone number must contain exactly 10 digits.")
    private String phone;

    @NotBlank(message = "Expertise is required.")
    @Size(max = 100, message = "Expertise must not exceed 100 characters.")
    private String expertise;

    @NotBlank(message = "Experience is required.")
    @Size(max = 50, message = "Experience must not exceed 50 characters.")
    private String experience;

    @NotBlank(message = "Status is required.")
    @Pattern(regexp = "^(Available|Unavailable)$", message = "Status must be Available or Unavailable.")
    private String status;

    @PastOrPresent(message = "Added date cannot be in the future.")
    private LocalDate addedDate;

    /** Optional Base64 profile image / resume. */
    private String profile;

    @NotBlank(message = "Shift timing is required.")
    @Size(max = 50, message = "Shift timing must not exceed 50 characters.")
    private String shiftTiming;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters.")
    private String remarks;

    public SupportAgentRequestDTO() {
    }

    public SupportAgentRequestDTO(String name, String email, String phone, String expertise, String experience,
            String status, String shiftTiming) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.expertise = expertise;
        this.experience = experience;
        this.status = status;
        this.shiftTiming = shiftTiming;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getAddedDate() {
        return addedDate;
    }

    public void setAddedDate(LocalDate addedDate) {
        this.addedDate = addedDate;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getShiftTiming() {
        return shiftTiming;
    }

    public void setShiftTiming(String shiftTiming) {
        this.shiftTiming = shiftTiming;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
