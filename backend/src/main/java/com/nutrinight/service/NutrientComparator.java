package com.nutrinight.service;

import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.Nutrient;
import com.nutrinight.dto.ComparisonDto;
import com.nutrinight.dto.ComparisonDto.NutrientDifference;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.mapper.FoodItemMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares two foods nutrient by nutrient. A recommendation requires sufficient data and improvements in at
 * least two "limit" nutrients (energy, sugar, sodium, saturated fat) with at most one worse; a single better
 * nutrient is never enough to call a food preferable.
 */
@Component
public class NutrientComparator {

    /** Differences smaller than this (relative to the source) are treated as similar. */
    static final double SIMILAR_THRESHOLD_PERCENT = 10.0;
    static final int MIN_FAVOURABLE = 2;
    static final int MAX_UNFAVOURABLE = 1;
    /** At least this many of the four limit nutrients must be known for both foods. */
    static final int MIN_LIMIT_NUTRIENTS_KNOWN = 3;

    enum MealRole { BEVERAGE, SWEET, SNACK, MAIN }

    static MealRole role(FoodItem item) {
        if (item.isBeverage() || item.getCategory() == FoodCategory.SUGARY_BEVERAGES) {
            return MealRole.BEVERAGE;
        }
        return switch (item.getCategory()) {
            case DESSERTS -> MealRole.SWEET;
            case FRIED_SNACKS -> MealRole.SNACK;
            case FRUITS_VEGETABLES -> MealRole.SNACK;
            default -> MealRole.MAIN;
        };
    }

    /** Whether {@code alternative} can plausibly replace {@code source} in the same eating occasion. */
    public boolean isCompatible(FoodItem source, FoodItem alternative) {
        MealRole s = role(source);
        MealRole a = role(alternative);
        if (s == a) {
            return true;
        }
        // Fruit can stand in for desserts; fruit/veg and fruit-based dishes can replace snacks.
        return (s == MealRole.SWEET && alternative.getCategory() == FoodCategory.FRUITS_VEGETABLES)
                || (s == MealRole.SNACK && alternative.getCategory() == FoodCategory.FRUITS_VEGETABLES);
    }

    public ComparisonDto compare(FoodItem source, FoodItem alternative) {
        boolean per100Comparable = source.getServingSize() != null && alternative.getServingSize() != null
                && source.getServingUnit() != null && source.getServingUnit().equals(alternative.getServingUnit());
        List<NutrientDifference> diffs = new ArrayList<>();
        int favourable = 0;
        int unfavourable = 0;
        int limitKnown = 0;
        double score = 0;
        List<String> lower = new ArrayList<>();
        List<String> higher = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        List<String> servingDriven = new ArrayList<>();

        for (Nutrient n : Nutrient.values()) {
            BigDecimal sv = n.perServing(source);
            BigDecimal av = n.perServing(alternative);
            BigDecimal s100 = per100Comparable ? per100(sv, source.getServingSize()) : null;
            BigDecimal a100 = per100Comparable ? per100(av, alternative.getServingSize()) : null;
            if (sv == null || av == null) {
                unavailable.add(n.label().toLowerCase());
                diffs.add(new NutrientDifference(n.name(), n.label(), n.unit(), sv, av, s100, a100, null,
                        "UNAVAILABLE", "UNKNOWN"));
                continue;
            }
            if (n.lowerIsBetter()) {
                limitKnown++;
            }
            Double pct = percentDifference(sv, av);
            String direction;
            if (pct == null) {
                direction = av.signum() == 0 ? "SIMILAR" : "HIGHER"; // source is 0
            } else if (Math.abs(pct) < SIMILAR_THRESHOLD_PERCENT) {
                direction = "SIMILAR";
            } else {
                direction = pct < 0 ? "LOWER" : "HIGHER";
            }
            String assessment = "NEUTRAL";
            if (!direction.equals("SIMILAR")) {
                boolean better = direction.equals("LOWER") == n.lowerIsBetter();
                assessment = better ? "FAVOURABLE" : "UNFAVOURABLE";
                double weight = n.lowerIsBetter() ? 1.0 : 0.5;
                if (n.lowerIsBetter()) {
                    if (better) {
                        favourable++;
                    } else {
                        unfavourable++;
                    }
                }
                score += better ? weight : -weight;
                // A per-serving advantage that disappears per 100 g/ml comes only from a smaller portion.
                if (better && n.lowerIsBetter() && s100 != null && a100 != null && s100.signum() > 0
                        && a100.compareTo(s100.multiply(BigDecimal.valueOf(1 - SIMILAR_THRESHOLD_PERCENT / 100))) > 0) {
                    servingDriven.add(n.label().toLowerCase());
                }
                (direction.equals("LOWER") ? lower : higher).add(n.label().toLowerCase()
                        + (pct == null ? "" : " (" + Math.round(Math.abs(pct)) + "%)"));
            }
            diffs.add(new NutrientDifference(n.name(), n.label(), n.unit(), sv, av, s100, a100,
                    pct == null ? null : Math.round(pct * 10) / 10.0, direction, assessment));
        }

        boolean sufficient = limitKnown >= MIN_LIMIT_NUTRIENTS_KNOWN;
        boolean recommended = sufficient && favourable >= MIN_FAVOURABLE && unfavourable <= MAX_UNFAVOURABLE
                && isCompatible(source, alternative);
        String summary = summarize(source, alternative, sufficient, lower, higher, unavailable, recommended);
        if (sufficient && !servingDriven.isEmpty()) {
            summary += " Note: per 100 " + alternative.getServingUnit() + ", " + String.join(", ", servingDriven)
                    + " is not lower; that advantage comes from the smaller serving size.";
        }
        return new ComparisonDto(FoodItemMapper.toDto(source), FoodItemMapper.toDto(alternative), sufficient,
                per100Comparable, favourable, unfavourable, Math.round(score * 100) / 100.0, recommended, summary, diffs);
    }

    private static String summarize(FoodItem source, FoodItem alt, boolean sufficient, List<String> lower,
                                    List<String> higher, List<String> unavailable, boolean recommended) {
        if (!sufficient) {
            return "No reliable comparison can be made: nutrition data for " + source.getName() + " or "
                    + alt.getName() + " is insufficient (fewer than " + MIN_LIMIT_NUTRIENTS_KNOWN
                    + " of energy, sugar, sodium and saturated fat are known for both).";
        }
        StringBuilder sb = new StringBuilder("Per serving (")
                .append(servingText(alt)).append(" vs ").append(servingText(source)).append("), ")
                .append(alt.getName()).append(" has ");
        sb.append(lower.isEmpty() ? "no meaningfully lower values" : "lower " + String.join(", ", lower));
        if (!higher.isEmpty()) {
            sb.append("; higher ").append(String.join(", ", higher));
        }
        sb.append(" compared with ").append(source.getName()).append('.');
        if (!unavailable.isEmpty()) {
            sb.append(" Not comparable (missing data): ").append(String.join(", ", unavailable)).append('.');
        }
        sb.append(recommended
                ? " It may be a preferable choice on the available data, though overall diet quality depends on more than these nutrients."
                : " It is not recommended as an alternative because the improvement is not consistent across nutrients or the foods serve different purposes.");
        return sb.toString();
    }

    private static String servingText(FoodItem item) {
        return item.getServingSize() == null ? "serving size unknown"
                : item.getServingSize().stripTrailingZeros().toPlainString() + " " + item.getServingUnit();
    }

    static Double percentDifference(BigDecimal source, BigDecimal alternative) {
        if (source.signum() == 0) {
            return null;
        }
        return alternative.subtract(source).divide(source, 6, RoundingMode.HALF_UP).doubleValue() * 100.0;
    }

    static BigDecimal per100(BigDecimal value, BigDecimal servingSize) {
        if (value == null || servingSize == null || servingSize.signum() == 0) {
            return null;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(servingSize, 2, RoundingMode.HALF_UP);
    }
}
