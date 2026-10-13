package com.nutrinight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_food_item_id")
    private FoodItem sourceFoodItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alternative_food_item_id")
    private FoodItem alternativeFoodItem;

    @Column(name = "improvement_score", nullable = false)
    private BigDecimal improvementScore;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Recommendation() {
    }

    public Recommendation(FoodItem sourceFoodItem, FoodItem alternativeFoodItem, BigDecimal improvementScore,
                          String explanation) {
        this.sourceFoodItem = sourceFoodItem;
        this.alternativeFoodItem = alternativeFoodItem;
        this.improvementScore = improvementScore;
        this.explanation = explanation;
    }

    public Long getId() {
        return id;
    }

    public FoodItem getSourceFoodItem() {
        return sourceFoodItem;
    }

    public FoodItem getAlternativeFoodItem() {
        return alternativeFoodItem;
    }

    public BigDecimal getImprovementScore() {
        return improvementScore;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
