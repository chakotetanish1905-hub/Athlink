package com.examly.springapp.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Stores a float[] embedding vector as text, for example "0.12,-0.5,0.33".
@Converter
public class EmbeddingConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
