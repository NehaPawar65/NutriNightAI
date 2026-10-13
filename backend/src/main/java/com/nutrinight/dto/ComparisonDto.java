package com.nutrinight.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Side-by-side nutrient comparison of two foods. Assessments are made per serving (what one order line
 * contains); per-100 g/ml values are included only when both items use the same serving unit.
 */
public record ComparisonDto(
        FoodItemDto source,
        FoodItemDto alternative,
        boolean sufficientData,
        boolean per100Comparable,
        int favourableCount,
        int unfavourableCount,
        double score,
        boolean recommended,
        String summary,
        List<NutrientDifference> nutrients) {

    /**
     * @param direction  LOWER, HIGHER, SIMILAR (within ±10%) or UNAVAILABLE (alternative relative to source)
     * @param assessment FAVOURABLE, UNFAVOURABLE, NEUTRAL or UNKNOWN
     */
    public record NutrientDifference(String nutrient, String label, String unit, BigDecimal sourcePerServing,
                                     BigDecimal alternativePerServing, BigDecimal sourcePer100,
                                     BigDecimal alternativePer100, Double differencePercent, String direction,
                                     String assessment) {
    }
}
