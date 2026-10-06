package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Priority of a support ticket.
// The label (for example "High") is what the API (JSON) and the database use, exactly as before.
public enum TicketPriority {

    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String label;

    TicketPriority(String label) {
        this.label = label;
    }

    // Written to JSON as the label, for example "High"
    @JsonValue
    public String getLabel() {
        return label;
    }

    // Read from JSON: an empty value becomes null (so @NotNull can report "required"),
    // an unknown value is rejected with a clear 400 message.
    @JsonCreator
    public static TicketPriority fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        for (TicketPriority value : values()) {
            if (value.label.equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Priority must be High, Medium or Low");
    }

    // Stores the label in the database column (not the constant name and not the ordinal),
    // so existing rows keep working without a data migration.
    @Converter
    public static class DbConverter implements AttributeConverter<TicketPriority, String> {

        @Override
        public String convertToDatabaseColumn(TicketPriority value) {
            return value == null ? null : value.getLabel();
        }

        @Override
        public TicketPriority convertToEntityAttribute(String label) {
            return fromLabel(label);
        }
    }
}
