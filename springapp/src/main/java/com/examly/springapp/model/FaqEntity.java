package com.examly.springapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

// One FAQ of the chatbot knowledge base (table: faqs).
@Entity
@Table(name = "faqs")
public class FaqEntity {

    // The id comes from faqs.json, so it is not auto-generated
    @Id
    private Long id;

    private String category;

    @Column(length = 500)
    private String question;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answer;

    // Semantic vector from Gemini, stored as text. Never sent to the browser.
    @JsonIgnore
    @Column(columnDefinition = "LONGTEXT")
    @Convert(converter = EmbeddingConverter.class)
    private float[] embedding;

    public FaqEntity() {
    }

    public FaqEntity(Long id, String category, String question, String answer) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.answer = answer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
