package com.nutrinight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "model_runs")
public class ModelRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_metrics")
    private String evaluationMetrics;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ModelRun() {
    }

    public ModelRun(String modelName, String modelVersion, String evaluationMetrics, String datasetVersion) {
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.evaluationMetrics = evaluationMetrics;
        this.datasetVersion = datasetVersion;
    }

    public Long getId() {
        return id;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getEvaluationMetrics() {
        return evaluationMetrics;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
