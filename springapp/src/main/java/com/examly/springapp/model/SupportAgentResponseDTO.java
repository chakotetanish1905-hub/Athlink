package com.examly.springapp.model;

import java.time.LocalDate;

/**
 * SupportAgent as returned by the API.
 */
public class SupportAgentResponseDTO {

    private Long agentId;
    private String name;
    private String email;
    private String phone;
    private String expertise;
    private String experience;
    private String status;
    private LocalDate addedDate;
    private String profile;
    private String shiftTiming;
    private String remarks;

    public SupportAgentResponseDTO() {
    }

    public static SupportAgentResponseDTO from(SupportAgent agent) {
        if (agent == null) {
            return null;
        }
        SupportAgentResponseDTO dto = new SupportAgentResponseDTO();
        dto.setAgentId(agent.getAgentId());
        dto.setName(agent.getName());
        dto.setEmail(agent.getEmail());
        dto.setPhone(agent.getPhone());
        dto.setExpertise(agent.getExpertise());
        dto.setExperience(agent.getExperience());
        dto.setStatus(agent.getStatus());
        dto.setAddedDate(agent.getAddedDate());
        dto.setProfile(agent.getProfile());
        dto.setShiftTiming(agent.getShiftTiming());
        dto.setRemarks(agent.getRemarks());
        return dto;
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
