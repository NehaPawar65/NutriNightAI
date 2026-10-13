package com.nutrinight.entity;

import com.nutrinight.domain.CategorySource;
import com.nutrinight.domain.FoodCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * A food or drink with per-serving nutrition. A {@code null} nutrient means "not available";
 * it is never treated as zero.
 */
@Entity
@Table(name = "food_items")
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false)
    private CategorySource categorySource = CategorySource.PROVIDED;

    /** Grams, or millilitres when {@link #servingUnit} is "ml". */
    @Column(name = "serving_size")
    private BigDecimal servingSize;

    @Column(name = "serving_unit")
    private String servingUnit;

    @Column(name = "calories_kcal")
    private BigDecimal caloriesKcal;

    @Column(name = "sugar_g")
    private BigDecimal sugarG;

    @Column(name = "sodium_mg")
    private BigDecimal sodiumMg;

    @Column(name = "saturated_fat_g")
    private BigDecimal saturatedFatG;

    @Column(name = "protein_g")
    private BigDecimal proteinG;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nutrition_source_id")
    private NutritionSource nutritionSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected FoodItem() {
    }

    public FoodItem(String name, FoodCategory category, CategorySource categorySource) {
        this.name = name;
        this.normalizedName = normalize(name);
        this.category = category;
        this.categorySource = categorySource;
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public boolean isBeverage() {
        return "ml".equals(servingUnit);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public FoodCategory getCategory() {
        return category;
    }

    public void setCategory(FoodCategory category) {
        this.category = category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public BigDecimal getServingSize() {
        return servingSize;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServing(BigDecimal servingSize, String servingUnit) {
        this.servingSize = servingSize;
        this.servingUnit = servingUnit;
    }

    public BigDecimal getCaloriesKcal() {
        return caloriesKcal;
    }

    public BigDecimal getSugarG() {
        return sugarG;
    }

    public BigDecimal getSodiumMg() {
        return sodiumMg;
    }

    public BigDecimal getSaturatedFatG() {
        return saturatedFatG;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }

    public void setNutrients(BigDecimal caloriesKcal, BigDecimal sugarG, BigDecimal sodiumMg,
                             BigDecimal saturatedFatG, BigDecimal proteinG) {
        this.caloriesKcal = caloriesKcal;
        this.sugarG = sugarG;
        this.sodiumMg = sodiumMg;
        this.saturatedFatG = saturatedFatG;
        this.proteinG = proteinG;
    }

    public NutritionSource getNutritionSource() {
        return nutritionSource;
    }

    public void setNutritionSource(NutritionSource nutritionSource) {
        this.nutritionSource = nutritionSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
