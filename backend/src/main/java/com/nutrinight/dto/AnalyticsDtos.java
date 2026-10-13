package com.nutrinight.dto;

import com.nutrinight.service.NutritionCalculator.NutrientStat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response shapes for /api/analytics/**. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record WindowInfo(String start, String end, String timezone, String rule) {
    }

    public record Scope(LocalDate from, LocalDate to, String city, String category, boolean lateNightOnly,
                        LocalDate dataStart, LocalDate dataEnd) {
    }

    public record CategoryCount(String category, String label, long orders, Double sharePercent) {
    }

    public record Overview(Scope scope, WindowInfo window, long totalOrders, long lateNightOrders,
                           Double lateNightPercentage, Integer peakHour, Integer peakLateNightHour,
                           long analysisDays, int cities, BigDecimal meanKcalPerLateNightOrder,
                           List<CategoryCount> topLateNightCategories, List<String> dataSources) {
    }

    public record HourBucket(int hour, boolean lateNight, long orders, long weekdayOrders, long weekendOrders,
                             Double weekdayOrdersPerDay, Double weekendOrdersPerDay, BigDecimal meanKcalPerOrder) {
    }

    public record DayTypeSummary(String dayType, long days, long orders, long lateNightOrders,
                                 Double lateNightOrdersPerDay, Double lateNightSharePercent,
                                 BigDecimal meanKcalPerLateNightOrder, Double discretionarySharePercent) {
    }

    public record Hourly(Scope scope, WindowInfo window, List<HourBucket> hours, List<DayTypeSummary> dayTypes,
                         String note) {
    }

    public record CategoryStats(String category, String label, long orders, long servings, Double sharePercent,
                                long weekdayOrders, long weekendOrders, Map<String, BigDecimal> meanPerOrder) {
    }

    public record Categories(Scope scope, long totalOrders, List<CategoryStats> categories) {
    }

    public record LevelDistribution(String nutrient, String label, long low, long medium, long high, long unknown) {
    }

    public record CategoryNutrition(String category, String label, long orders, List<NutrientStat> nutrients) {
    }

    public record ItemUsage(FoodItemDto item, long orders, Map<String, String> levels) {
    }

    public record Thresholds(String source, String sourceUrl, Map<String, Map<String, Double>> food,
                             Map<String, Map<String, Double>> drinks) {
    }

    public record Nutrition(Scope scope, long orderCount, List<NutrientStat> nutrients,
                            List<CategoryNutrition> byCategory, List<LevelDistribution> levelDistribution,
                            Thresholds thresholds, List<ItemUsage> foodItems, List<String> definitions) {
    }

    public record CityStats(String city, boolean suppressed, Long orders, Long lateNightOrders,
                            Double lateNightSharePercent, BigDecimal meanKcalPerLateNightOrder,
                            Double discretionarySharePercent) {
    }

    public record Geographic(Scope scope, int minGroupSize, int suppressedGroups, List<CityStats> cities, String note) {
    }

    public record FilterOptions(List<String> cities, List<Map<String, String>> categories, LocalDate minDate,
                                LocalDate maxDate, WindowInfo window) {
    }
}
