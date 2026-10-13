package com.nutrinight.dto;

import java.math.BigDecimal;
import java.util.List;

public record FoodItemDto(Long id, String name, String category, String categoryLabel, String categorySource,
                          BigDecimal servingSize, String servingUnit, BigDecimal caloriesKcal, BigDecimal sugarG,
                          BigDecimal sodiumMg, BigDecimal saturatedFatG, BigDecimal proteinG,
                          String nutritionSourceName, String nutritionSourceUrl, List<String> missingNutrients) {
}
