package com.nutrinight.domain;

import com.nutrinight.entity.FoodItem;

import java.math.BigDecimal;
import java.util.function.Function;

/** Nutrients tracked per serving. {@code lowerIsBetter} reflects common dietary guidance for NCD prevention. */
public enum Nutrient {
    CALORIES("Energy", "kcal", true, FoodItem::getCaloriesKcal),
    SUGAR("Sugar", "g", true, FoodItem::getSugarG),
    SODIUM("Sodium", "mg", true, FoodItem::getSodiumMg),
    SATURATED_FAT("Saturated fat", "g", true, FoodItem::getSaturatedFatG),
    PROTEIN("Protein", "g", false, FoodItem::getProteinG);

    private final String label;
    private final String unit;
    private final boolean lowerIsBetter;
    private final Function<FoodItem, BigDecimal> accessor;

    Nutrient(String label, String unit, boolean lowerIsBetter, Function<FoodItem, BigDecimal> accessor) {
        this.label = label;
        this.unit = unit;
        this.lowerIsBetter = lowerIsBetter;
        this.accessor = accessor;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public boolean lowerIsBetter() {
        return lowerIsBetter;
    }

    /** Per-serving value, or {@code null} when unavailable. */
    public BigDecimal perServing(FoodItem item) {
        return accessor.apply(item);
    }
}
