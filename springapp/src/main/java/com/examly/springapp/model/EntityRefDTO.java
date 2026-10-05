package com.examly.springapp.model;

/**
 * Lightweight reference used when a client posts a nested object such as
 * {"user": {"userId": 1}} or {"supportAgent": {"agentId": 2}} instead of a flat id.
 */
public class EntityRefDTO {

    private Long userId;
    private Long agentId;
    private Long ticketId;

    public EntityRefDTO() {
    }

    public EntityRefDTO(Long userId, Long agentId, Long ticketId) {
        this.userId = userId;
        this.agentId = agentId;
        this.ticketId = ticketId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
