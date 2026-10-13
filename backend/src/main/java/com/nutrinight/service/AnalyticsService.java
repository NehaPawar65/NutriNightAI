package com.nutrinight.service;

import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.LateNightWindow;
import com.nutrinight.domain.Nutrient;
import com.nutrinight.dto.AnalyticsDtos.Categories;
import com.nutrinight.dto.AnalyticsDtos.CategoryCount;
import com.nutrinight.dto.AnalyticsDtos.CategoryNutrition;
import com.nutrinight.dto.AnalyticsDtos.CategoryStats;
import com.nutrinight.dto.AnalyticsDtos.CityStats;
import com.nutrinight.dto.AnalyticsDtos.DayTypeSummary;
import com.nutrinight.dto.AnalyticsDtos.FilterOptions;
import com.nutrinight.dto.AnalyticsDtos.Geographic;
import com.nutrinight.dto.AnalyticsDtos.HourBucket;
import com.nutrinight.dto.AnalyticsDtos.Hourly;
import com.nutrinight.dto.AnalyticsDtos.ItemUsage;
import com.nutrinight.dto.AnalyticsDtos.LevelDistribution;
import com.nutrinight.dto.AnalyticsDtos.Nutrition;
import com.nutrinight.dto.AnalyticsDtos.Overview;
import com.nutrinight.dto.AnalyticsDtos.Scope;
import com.nutrinight.dto.AnalyticsDtos.Thresholds;
import com.nutrinight.dto.AnalyticsDtos.WindowInfo;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.mapper.FoodItemMapper;
import com.nutrinight.repository.AnalyticsRepository;
import com.nutrinight.repository.AnalyticsRepository.Cell;
import com.nutrinight.repository.AnalyticsRepository.CityCell;
import com.nutrinight.repository.AnalyticsRepository.DataRange;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.service.NutritionCalculator.Level;
import com.nutrinight.service.NutritionCalculator.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Descriptive analytics over imported orders. Every figure is computed from the database. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final List<Nutrient> LEVEL_NUTRIENTS = List.of(Nutrient.SUGAR, Nutrient.SATURATED_FAT, Nutrient.SODIUM);

    private final AnalyticsRepository repository;
    private final FoodItemRepository foodItemRepository;
    private final NutriNightProperties properties;
    private final LateNightWindow window;

    public AnalyticsService(AnalyticsRepository repository, FoodItemRepository foodItemRepository,
                            NutriNightProperties properties, LateNightWindow window) {
        this.repository = repository;
        this.foodItemRepository = foodItemRepository;
        this.properties = properties;
        this.window = window;
    }

    public WindowInfo windowInfo() {
        return new WindowInfo(window.start().toString(), window.end().toString(), properties.timezone(),
                "Start inclusive, end exclusive, local time; orders before " + window.end()
                        + " are attributed to the previous evening's analysis date.");
    }

    public Overview overview(OrderFilter filter) {
        OrderFilter all = filter.withLateNightOnly(false);
        List<Cell> cells = repository.cells(all);
        Map<Long, FoodItem> items = items();
        DataRange range = repository.range(all);
        long total = sum(cells, c -> true);
        long late = sum(cells, Cell::lateNight);

        Map<Integer, Long> byHour = cells.stream().collect(Collectors.groupingBy(Cell::hour, Collectors.summingLong(Cell::orders)));
        Map<Integer, Long> byLateHour = cells.stream().filter(Cell::lateNight)
                .collect(Collectors.groupingBy(Cell::hour, Collectors.summingLong(Cell::orders)));

        Map<FoodCategory, Long> lateByCategory = new EnumMap<>(FoodCategory.class);
        cells.stream().filter(Cell::lateNight)
                .forEach(c -> lateByCategory.merge(items.get(c.foodItemId()).getCategory(), c.orders(), Long::sum));
        List<CategoryCount> top = lateByCategory.entrySet().stream()
                .sorted(Map.Entry.<FoodCategory, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new CategoryCount(e.getKey().name(), e.getKey().getLabel(), e.getValue(), percent(e.getValue(), late)))
                .toList();

        int cities = (int) repository.cityCells(all).stream().map(CityCell::city).distinct().count();
        return new Overview(scope(filter, false, range), windowInfo(), total, late, percent(late, total),
                argMax(byHour), argMax(byLateHour), range.weekdayDays() + range.weekendDays(), cities,
                NutritionCalculator.meanPerOrder(lines(cells, Cell::lateNight, items), Nutrient.CALORIES), top,
                repository.distinctDataSources(all));
    }

    public Hourly hourly(OrderFilter filter) {
        OrderFilter all = filter.withLateNightOnly(false);
        List<Cell> cells = repository.cells(all);
        Map<Long, FoodItem> items = items();
        DataRange range = repository.range(all);

        List<HourBucket> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int hour = h;
            long weekday = sum(cells, c -> c.hour() == hour && !c.weekend());
            long weekend = sum(cells, c -> c.hour() == hour && c.weekend());
            hours.add(new HourBucket(h, window.contains(LocalTime.of(h, 0)), weekday + weekend, weekday, weekend,
                    perDay(weekday, range.weekdayDays()), perDay(weekend, range.weekendDays()),
                    NutritionCalculator.meanPerOrder(lines(cells, c -> c.hour() == hour, items), Nutrient.CALORIES)));
        }
        List<DayTypeSummary> dayTypes = List.of(
                dayType("WEEKDAY", false, cells, items, range.weekdayDays()),
                dayType("WEEKEND", true, cells, items, range.weekendDays()));
        return new Hourly(scope(filter, false, range), windowInfo(), hours, dayTypes,
                "Weekend = analysis days that are Saturday or Sunday (Saturday and Sunday nights, including their "
                        + "after-midnight hours). Per-day values divide by the number of days of that type present in the data.");
    }

    public Categories categories(OrderFilter filter) {
        List<Cell> cells = repository.cells(filter);
        Map<Long, FoodItem> items = items();
        DataRange range = repository.range(filter);
        long total = sum(cells, c -> true);

        Map<FoodCategory, List<Cell>> byCategory = cells.stream()
                .collect(Collectors.groupingBy(c -> items.get(c.foodItemId()).getCategory(),
                        () -> new EnumMap<>(FoodCategory.class), Collectors.toList()));
        List<CategoryStats> stats = byCategory.entrySet().stream().map(e -> {
            List<Cell> cs = e.getValue();
            long orders = sum(cs, c -> true);
            Map<String, BigDecimal> means = new LinkedHashMap<>();
            Collection<Line> ls = lines(cs, c -> true, items);
            for (Nutrient n : Nutrient.values()) {
                means.put(n.name(), NutritionCalculator.meanPerOrder(ls, n));
            }
            return new CategoryStats(e.getKey().name(), e.getKey().getLabel(), orders,
                    cs.stream().mapToLong(Cell::quantity).sum(), percent(orders, total),
                    sum(cs, c -> !c.weekend()), sum(cs, Cell::weekend), means);
        }).sorted(Comparator.comparingLong(CategoryStats::orders).reversed()).toList();
        return new Categories(scope(filter, true, range), total, stats);
    }

    public Nutrition nutrition(OrderFilter filter) {
        List<Cell> cells = repository.cells(filter);
        Map<Long, FoodItem> items = items();
        DataRange range = repository.range(filter);
        Collection<Line> allLines = lines(cells, c -> true, items);
        long total = sum(cells, c -> true);

        Map<FoodCategory, List<Line>> byCategory = allLines.stream()
                .collect(Collectors.groupingBy(l -> l.item().getCategory(), () -> new EnumMap<>(FoodCategory.class),
                        Collectors.toList()));
        List<CategoryNutrition> categoryNutrition = byCategory.entrySet().stream()
                .map(e -> new CategoryNutrition(e.getKey().name(), e.getKey().getLabel(),
                        e.getValue().stream().mapToLong(Line::orders).sum(), NutritionCalculator.summarize(e.getValue())))
                .sorted(Comparator.comparingLong(CategoryNutrition::orders).reversed())
                .toList();

        NutriNightProperties.NutritionThresholds t = properties.nutritionThresholds();
        List<LevelDistribution> levels = new ArrayList<>();
        for (Nutrient n : LEVEL_NUTRIENTS) {
            Map<Level, Long> counts = new EnumMap<>(Level.class);
            Arrays.stream(Level.values()).forEach(l -> counts.put(l, 0L));
            allLines.forEach(l -> counts.merge(NutritionCalculator.level(l.item(), n, t), l.orders(), Long::sum));
            levels.add(new LevelDistribution(n.name(), n.label(), counts.get(Level.LOW), counts.get(Level.MEDIUM),
                    counts.get(Level.HIGH), counts.get(Level.UNKNOWN)));
        }

        List<ItemUsage> usage = allLines.stream()
                .sorted(Comparator.comparingLong(Line::orders).reversed())
                .map(l -> {
                    Map<String, String> lv = new LinkedHashMap<>();
                    LEVEL_NUTRIENTS.forEach(n -> lv.put(n.name(), NutritionCalculator.level(l.item(), n, t).name()));
                    return new ItemUsage(FoodItemMapper.toDto(l.item()), l.orders(), lv);
                })
                .toList();

        return new Nutrition(scope(filter, true, range), total, NutritionCalculator.summarize(allLines),
                categoryNutrition, levels, thresholds(t), usage, List.of(
                "total: sum over order lines of per-serving value x quantity (order lines with known values only)",
                "meanPerOrder: total / order lines with a known value",
                "meanPerServing: mean per-serving value across order lines with a known value",
                "ordersMissing: order lines whose food item has no value for the nutrient (not counted as zero)",
                "levels: LOW / MEDIUM / HIGH per 100 g (food) or 100 ml (drinks) using the configured thresholds; "
                        + "UNKNOWN when the nutrient or serving size is missing"));
    }

    public Geographic geographic(OrderFilter filter) {
        OrderFilter all = filter.withLateNightOnly(false);
        List<CityCell> cells = repository.cityCells(all);
        Map<Long, FoodItem> items = items();
        int k = properties.privacy().minGroupSize();

        Map<String, List<CityCell>> byCity = cells.stream()
                .collect(Collectors.groupingBy(CityCell::city, LinkedHashMap::new, Collectors.toList()));
        List<CityStats> stats = new ArrayList<>();
        int suppressed = 0;
        for (Map.Entry<String, List<CityCell>> e : byCity.entrySet()) {
            List<CityCell> cs = e.getValue();
            long orders = cs.stream().mapToLong(CityCell::orders).sum();
            if (orders < k) {
                suppressed++;
                stats.add(new CityStats(e.getKey(), true, null, null, null, null, null));
                continue;
            }
            List<CityCell> lateCells = cs.stream().filter(CityCell::lateNight).toList();
            long late = lateCells.stream().mapToLong(CityCell::orders).sum();
            long discretionary = lateCells.stream()
                    .filter(c -> FoodCategory.DISCRETIONARY.contains(items.get(c.foodItemId()).getCategory()))
                    .mapToLong(CityCell::orders).sum();
            List<Line> lateLines = lateCells.stream()
                    .map(c -> new Line(items.get(c.foodItemId()), c.orders(), c.quantity())).toList();
            // Late-night details are also suppressed when the late-night subgroup itself is small.
            boolean lateSmall = late < k;
            stats.add(new CityStats(e.getKey(), false, orders, late, percent(late, orders),
                    lateSmall ? null : NutritionCalculator.meanPerOrder(lateLines, Nutrient.CALORIES),
                    lateSmall ? null : percent(discretionary, late)));
        }
        stats.sort(Comparator.comparing((CityStats s) -> s.orders() == null ? -1L : s.orders()).reversed());
        return new Geographic(scope(filter, false, repository.range(all)), k, suppressed, stats,
                "Results are aggregated at city level. Cities with fewer than " + k + " orders in the selection are "
                        + "suppressed, and late-night nutrition details are withheld when a city's late-night subgroup "
                        + "is below the same threshold.");
    }

    public FilterOptions filterOptions() {
        DataRange range = repository.range(OrderFilter.of(null, null, null, null, false));
        List<Map<String, String>> categories = Arrays.stream(FoodCategory.values())
                .map(c -> Map.of("value", c.name(), "label", c.getLabel())).toList();
        return new FilterOptions(repository.distinctCities(), categories, range.minDate(), range.maxDate(), windowInfo());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private Map<Long, FoodItem> items() {
        return foodItemRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(FoodItem::getId, Function.identity()));
    }

    private DayTypeSummary dayType(String name, boolean weekend, List<Cell> cells, Map<Long, FoodItem> items, long days) {
        long orders = sum(cells, c -> c.weekend() == weekend);
        long late = sum(cells, c -> c.weekend() == weekend && c.lateNight());
        long discretionary = sum(cells, c -> c.weekend() == weekend && c.lateNight()
                && FoodCategory.DISCRETIONARY.contains(items.get(c.foodItemId()).getCategory()));
        return new DayTypeSummary(name, days, orders, late, perDay(late, days), percent(late, orders),
                NutritionCalculator.meanPerOrder(lines(cells, c -> c.weekend() == weekend && c.lateNight(), items),
                        Nutrient.CALORIES), percent(discretionary, late));
    }

    static Collection<Line> lines(List<Cell> cells, Predicate<Cell> include, Map<Long, FoodItem> items) {
        Map<Long, long[]> agg = new HashMap<>();
        for (Cell c : cells) {
            if (include.test(c)) {
                long[] v = agg.computeIfAbsent(c.foodItemId(), id -> new long[2]);
                v[0] += c.orders();
                v[1] += c.quantity();
            }
        }
        return agg.entrySet().stream().map(e -> new Line(items.get(e.getKey()), e.getValue()[0], e.getValue()[1])).toList();
    }

    private static long sum(List<Cell> cells, Predicate<Cell> include) {
        return cells.stream().filter(include).mapToLong(Cell::orders).sum();
    }

    static Double percent(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static Double perDay(long count, long days) {
        return days == 0 ? null : Math.round(count * 100.0 / days) / 100.0;
    }

    private static Integer argMax(Map<Integer, Long> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse(null);
    }

    private Scope scope(OrderFilter f, boolean lateNightDefault, DataRange range) {
        boolean late = f.lateNightOnly() == null ? lateNightDefault : f.lateNightOnly();
        return new Scope(f.from(), f.to(), f.city(), f.category() == null ? null : f.category().name(), late,
                range.minDate(), range.maxDate());
    }

    private static Thresholds thresholds(NutriNightProperties.NutritionThresholds t) {
        return new Thresholds(t.source(), t.sourceUrl(), thresholdMap(t.food()), thresholdMap(t.drinks()));
    }

    private static Map<String, Map<String, Double>> thresholdMap(NutriNightProperties.ThresholdSet s) {
        Map<String, Map<String, Double>> m = new LinkedHashMap<>();
        m.put("SUGAR", Map.of("lowAtMost", s.sugarG().low(), "highAbove", s.sugarG().high()));
        m.put("SATURATED_FAT", Map.of("lowAtMost", s.saturatedFatG().low(), "highAbove", s.saturatedFatG().high()));
        m.put("SODIUM", Map.of("lowAtMost", s.sodiumMg().low(), "highAbove", s.sodiumMg().high()));
        return m;
    }
}
