package com.examly.springapp.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Stores all information related to support agents (SRS: SupportAgent).
 */
@Entity
@Table(name = "support_agent")
public class SupportAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long agentId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String phone;

    @NotBlank
    private String expertise;

    @NotBlank
    private String experience;

    @NotBlank
    @Pattern(regexp = "^(Available|Unavailable)$")
    private String status;

    private LocalDate addedDate;

    /** Base64-encoded profile image / resume. */
    @Lob
    @Column(nullable = true)
    private String profile;

    private String shiftTiming;

    @Column(length = 1000)
    private String remarks;

    public SupportAgent() {
    }

    public SupportAgent(String name, String email, String phone, String expertise, String experience, String status) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.expertise = expertise;
        this.experience = experience;
        this.status = status;
    }

    public SupportAgent(String name, String email, String phone, String expertise, String experience, String status,
            LocalDate addedDate, String profile, String shiftTiming, String remarks) {
        this(name, email, phone, expertise, experience, status);
        this.addedDate = addedDate;
        this.profile = profile;
        this.shiftTiming = shiftTiming;
        this.remarks = remarks;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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
