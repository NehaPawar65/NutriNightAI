package com.nutrinight.dto;

import java.math.BigDecimal;
import java.util.List;

/** Stored alternatives for one food, with live comparisons. */
public record RecommendationGroupDto(FoodItemDto source, boolean sufficientData, String message,
                                     List<Alternative> alternatives) {

    public record Alternative(Long recommendationId, BigDecimal improvementScore, String explanation,
                              ComparisonDto comparison) {
    }
}
