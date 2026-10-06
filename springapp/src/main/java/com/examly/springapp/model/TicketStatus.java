package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Lifecycle of a support ticket.
// The label (for example "In Progress") is what the API (JSON) and the database use, exactly as before.
public enum TicketStatus {

    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    // Written to JSON as the label, for example "In Progress"
    @JsonValue
    public String getLabel() {
        return label;
    }

    // Open or In Progress: an agent can still work on the ticket
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }

    // Resolved or Closed: the work is finished
    public boolean isResolvedOrClosed() {
        return this == RESOLVED || this == CLOSED;
    }

    // Read from JSON: an empty value becomes null (so @NotNull can report "required"),
    // an unknown value is rejected with a clear 400 message.
    @JsonCreator
    public static TicketStatus fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        for (TicketStatus value : values()) {
            if (value.label.equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Status must be Open, In Progress, Resolved or Closed");
    }

    // Stores the label in the database column (not the constant name and not the ordinal),
    // so existing rows keep working without a data migration.
    @Converter
    public static class DbConverter implements AttributeConverter<TicketStatus, String> {

        @Override
        public String convertToDatabaseColumn(TicketStatus value) {
            return value == null ? null : value.getLabel();
        }

        @Override
        public TicketStatus convertToEntityAttribute(String label) {
            return fromLabel(label);
        }
    }
}
