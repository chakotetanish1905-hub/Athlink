package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Availability of a support agent.
// The label (for example "Available") is what the API (JSON) and the database use, exactly as before.
public enum AgentStatus {

    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable");

    private final String label;

    AgentStatus(String label) {
        this.label = label;
    }

    // Written to JSON as the label, for example "Available"
    @JsonValue
    public String getLabel() {
        return label;
    }

    // Read from JSON: an empty value becomes null (so @NotNull can report "required"),
    // an unknown value is rejected with a clear 400 message.
    @JsonCreator
    public static AgentStatus fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        for (AgentStatus value : values()) {
            if (value.label.equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Status must be Available or Unavailable");
    }

    // Stores the label in the database column (not the constant name and not the ordinal),
    // so existing rows keep working without a data migration.
    @Converter
    public static class DbConverter implements AttributeConverter<AgentStatus, String> {

        @Override
        public String convertToDatabaseColumn(AgentStatus value) {
            return value == null ? null : value.getLabel();
        }

        @Override
        public AgentStatus convertToEntityAttribute(String label) {
            return fromLabel(label);
        }
    }
}
