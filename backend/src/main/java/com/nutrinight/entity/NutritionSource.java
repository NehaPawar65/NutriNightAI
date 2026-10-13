package com.nutrinight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "nutrition_sources")
public class NutritionSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, unique = true)
    private String sourceName;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected NutritionSource() {
    }

    public NutritionSource(String sourceName, String sourceUrl, String notes) {
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getReferenceDate() {
        return referenceDate;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
