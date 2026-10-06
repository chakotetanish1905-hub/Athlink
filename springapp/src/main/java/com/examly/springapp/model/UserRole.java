package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Role of a SupportSphere user.
// The label (for example "Manager") is what the API (JSON) and the database use, exactly as before.
public enum UserRole {

    MANAGER("Manager"),
    CLIENT("Client");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    // Written to JSON as the label, for example "Manager"
    @JsonValue
    public String getLabel() {
        return label;
    }

    // Read from JSON: an empty value becomes null (so @NotNull can report "required"),
    // an unknown value is rejected with a clear 400 message.
    @JsonCreator
    public static UserRole fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        for (UserRole value : values()) {
            if (value.label.equals(label)) {
                return value;
            }
        }
        throw new IllegalArgumentException("User role must be Manager or Client");
    }

    // Stores the label in the database column (not the constant name and not the ordinal),
    // so existing rows keep working without a data migration.
    @Converter
    public static class DbConverter implements AttributeConverter<UserRole, String> {

        @Override
        public String convertToDatabaseColumn(UserRole value) {
            return value == null ? null : value.getLabel();
        }

        @Override
        public UserRole convertToEntityAttribute(String label) {
            return fromLabel(label);
        }
    }
}
