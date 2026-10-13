package com.nutrinight.service;

import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.domain.Nutrient;
import com.nutrinight.entity.FoodItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Nutrient aggregation over order lines.
 * <ul>
 *   <li>total = sum of (per-serving value x quantity) over order lines whose value is known</li>
 *   <li>mean per order = total / number of order lines with a known value</li>
 *   <li>mean per serving = mean of per-serving values over order lines with a known value</li>
 * </ul>
 * Missing values are counted separately and never treated as zero.
 */
public final class NutritionCalculator {

    private NutritionCalculator() {
    }

    /** {@code orders} order lines of {@code item} containing {@code quantity} servings in total. */
    public record Line(FoodItem item, long orders, long quantity) {
    }

    public record NutrientStat(String nutrient, String label, String unit, BigDecimal total, BigDecimal meanPerOrder,
                               BigDecimal meanPerServing, long ordersWithData, long ordersMissing,
                               Double coveragePercent) {
    }

    public enum Level { LOW, MEDIUM, HIGH, UNKNOWN }

    public static List<NutrientStat> summarize(Collection<Line> lines) {
        List<NutrientStat> stats = new ArrayList<>();
        for (Nutrient n : Nutrient.values()) {
            stats.add(summarize(lines, n));
        }
        return stats;
    }

    public static NutrientStat summarize(Collection<Line> lines, Nutrient n) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal servingSum = BigDecimal.ZERO;
        long known = 0;
        long missing = 0;
        for (Line line : lines) {
            BigDecimal v = n.perServing(line.item());
            if (v == null) {
                missing += line.orders();
                continue;
            }
            known += line.orders();
            total = total.add(v.multiply(BigDecimal.valueOf(line.quantity())));
            servingSum = servingSum.add(v.multiply(BigDecimal.valueOf(line.orders())));
        }
        long all = known + missing;
        return new NutrientStat(n.name(), n.label(), n.unit(),
                known == 0 ? null : total.setScale(1, RoundingMode.HALF_UP),
                divide(total, known), divide(servingSum, known), known, missing,
                all == 0 ? null : Math.round(known * 1000.0 / all) / 10.0);
    }

    public static BigDecimal meanPerOrder(Collection<Line> lines, Nutrient n) {
        return summarize(lines, n).meanPerOrder();
    }

    /**
     * Front-of-pack style level for sugar, saturated fat or sodium, using per-100 g (food) or per-100 ml (drinks)
     * thresholds: LOW when value &lt;= low, HIGH when value &gt; high, MEDIUM otherwise.
     */
    public static Level level(FoodItem item, Nutrient n, NutriNightProperties.NutritionThresholds thresholds) {
        BigDecimal perServing = n.perServing(item);
        if (perServing == null || item.getServingSize() == null || item.getServingSize().signum() == 0) {
            return Level.UNKNOWN;
        }
        NutriNightProperties.ThresholdSet set = item.isBeverage() ? thresholds.drinks() : thresholds.food();
        NutriNightProperties.Range range = switch (n) {
            case SUGAR -> set.sugarG();
            case SATURATED_FAT -> set.saturatedFatG();
            case SODIUM -> set.sodiumMg();
            default -> throw new IllegalArgumentException("No thresholds for " + n);
        };
        double per100 = perServing.doubleValue() * 100.0 / item.getServingSize().doubleValue();
        if (per100 <= range.low()) {
            return Level.LOW;
        }
        return per100 > range.high() ? Level.HIGH : Level.MEDIUM;
    }

    private static BigDecimal divide(BigDecimal sum, long count) {
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
