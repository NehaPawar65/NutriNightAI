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
import java.time.LocalDate;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_type", nullable = false)
    private String analysisType;

    @Column(name = "analysis_period_start")
    private LocalDate periodStart;

    @Column(name = "analysis_period_end")
    private LocalDate periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_json")
    private String filtersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AnalysisResult() {
    }

    public AnalysisResult(String analysisType, LocalDate periodStart, LocalDate periodEnd,
                          String filtersJson, String resultJson) {
        this.analysisType = analysisType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.filtersJson = filtersJson;
        this.resultJson = resultJson;
    }

    public Long getId() {
        return id;
    }

    public String getAnalysisType() {
        return analysisType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getFiltersJson() {
        return filtersJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
