package com.nutrinight.service;

import com.nutrinight.dto.AnalyticsDtos;
import com.nutrinight.dto.OrderDto;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.repository.AnalyticsRepository;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.repository.FoodOrderRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** CSV exports and report metadata (data sources, methodology, limitations). */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public enum ReportType { SUMMARY, ORDERS, CATEGORIES, HOURLY, NUTRITION, GEOGRAPHIC }

    public static final int MAX_EXPORT_ROWS = 200_000;
    private static final int PAGE = 2000;

    public static final List<String> LIMITATIONS = List.of(
            "The bundled dataset is SYNTHETIC: orders and nutrition values are illustrative estimates, not verified "
                    + "restaurant or platform data.",
            "Food-order data describes purchasing behaviour, not consumption: items may be shared, partly eaten or "
                    + "ordered for others.",
            "No result is a diagnosis. Food-order data alone cannot establish whether anyone has obesity, diabetes or "
                    + "cardiovascular disease, nor an individual's risk of developing them.",
            "Nutrient values are per standard serving; actual portions and recipes vary by restaurant.",
            "Missing nutrient values are excluded from means (reported as coverage), never treated as zero.",
            "The dietary pattern indicator uses transparent but non-validated weights and reference values.",
            "Observed differences (e.g. weekday vs weekend) are descriptive associations, not causal effects.",
            "Small groups are suppressed (privacy), so city-level figures may not sum to overall totals.");

    private final AnalyticsService analyticsService;
    private final AnalyticsRepository analyticsRepository;
    private final FoodOrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;

    public ReportService(AnalyticsService analyticsService, AnalyticsRepository analyticsRepository,
                         FoodOrderRepository orderRepository, FoodItemRepository foodItemRepository) {
        this.analyticsService = analyticsService;
        this.analyticsRepository = analyticsRepository;
        this.orderRepository = orderRepository;
        this.foodItemRepository = foodItemRepository;
    }

    public static ReportType parseType(String type) {
        try {
            return ReportType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("'type' must be one of " + List.of(ReportType.values()));
        }
    }

    public String fileName(ReportType type) {
        return "nutrinight-" + type.name().toLowerCase() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
    }

    public Map<String, Object> metadata(OrderFilter filter) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        m.put("period", Map.of("from", Objects.toString(filter.from(), "all"), "to", Objects.toString(filter.to(), "all")));
        m.put("filters", Map.of("city", Objects.toString(filter.city(), "all"),
                "category", filter.category() == null ? "all" : filter.category().name()));
        m.put("lateNightWindow", analyticsService.windowInfo());
        m.put("dataSources", analyticsRepository.distinctDataSources(filter.withLateNightOnly(false)));
        List<Map<String, String>> nutritionSources = new ArrayList<>();
        foodItemRepository.findAllByOrderByNameAsc().stream()
                .map(FoodItem::getNutritionSource).filter(Objects::nonNull).distinct()
                .forEach(s -> nutritionSources.add(Map.of("name", s.getSourceName(),
                        "url", Objects.toString(s.getSourceUrl(), ""))));
        m.put("nutritionSources", nutritionSources);
        m.put("limitations", LIMITATIONS);
        return m;
    }

    public void write(ReportType type, OrderFilter filter, Writer writer) {
        try (CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            switch (type) {
                case ORDERS -> writeOrders(filter, csv);
                case CATEGORIES -> writeCategories(filter, csv);
                case HOURLY -> writeHourly(filter, csv);
                case NUTRITION -> writeNutrition(filter, csv);
                case GEOGRAPHIC -> writeGeographic(filter, csv);
                case SUMMARY -> writeSummary(filter, csv);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeOrders(OrderFilter filter, CSVPrinter csv) throws IOException {
        csv.printRecord("order_ref", "order_timestamp_utc", "local_order_time", "analysis_date", "local_hour",
                "late_night", "food_item", "category", "quantity", "city", "data_source", "calories_kcal_per_order",
                "sugar_g_per_order", "sodium_mg_per_order", "saturated_fat_g_per_order", "protein_g_per_order");
        int written = 0;
        for (int page = 0; ; page++) {
            Page<com.nutrinight.entity.FoodOrder> p = orderRepository.findAll(OrderService.specification(filter),
                    PageRequest.of(page, PAGE, Sort.by("orderTimestamp", "id")));
            for (var o : p.getContent()) {
                OrderDto d = OrderService.toDto(o);
                row(csv, d.orderRef(), d.orderTimestamp(), d.localOrderTime(), d.analysisDate(), d.localHour(),
                        d.lateNight(), d.foodItem(), d.category(), d.quantity(), d.city(), d.dataSource(),
                        d.caloriesKcalPerOrder(), d.sugarGPerOrder(), d.sodiumMgPerOrder(), d.saturatedFatGPerOrder(),
                        d.proteinGPerOrder());
                if (++written >= MAX_EXPORT_ROWS) {
                    return;
                }
            }
            if (!p.hasNext()) {
                return;
            }
        }
    }

    private void writeCategories(OrderFilter filter, CSVPrinter csv) throws IOException {
        AnalyticsDtos.Categories c = analyticsService.categories(filter);
        csv.printRecord("category", "label", "orders", "share_percent", "servings", "weekday_orders", "weekend_orders",
                "mean_kcal_per_order", "mean_sugar_g_per_order", "mean_sodium_mg_per_order",
                "mean_saturated_fat_g_per_order", "mean_protein_g_per_order");
        for (var s : c.categories()) {
            var m = s.meanPerOrder();
            row(csv, s.category(), s.label(), s.orders(), s.sharePercent(), s.servings(), s.weekdayOrders(),
                    s.weekendOrders(), m.get("CALORIES"), m.get("SUGAR"), m.get("SODIUM"), m.get("SATURATED_FAT"),
                    m.get("PROTEIN"));
        }
    }

    private void writeHourly(OrderFilter filter, CSVPrinter csv) throws IOException {
        AnalyticsDtos.Hourly h = analyticsService.hourly(filter);
        csv.printRecord("hour", "late_night", "orders", "weekday_orders", "weekend_orders", "weekday_orders_per_day",
                "weekend_orders_per_day", "mean_kcal_per_order");
        for (var b : h.hours()) {
            row(csv, b.hour(), b.lateNight(), b.orders(), b.weekdayOrders(), b.weekendOrders(), b.weekdayOrdersPerDay(),
                    b.weekendOrdersPerDay(), b.meanKcalPerOrder());
        }
    }

    private void writeNutrition(OrderFilter filter, CSVPrinter csv) throws IOException {
        AnalyticsDtos.Nutrition n = analyticsService.nutrition(filter);
        csv.printRecord("group", "nutrient", "unit", "total", "mean_per_order", "mean_per_serving", "orders_with_data",
                "orders_missing", "coverage_percent");
        for (var s : n.nutrients()) {
            row(csv, "ALL", s.nutrient(), s.unit(), s.total(), s.meanPerOrder(), s.meanPerServing(), s.ordersWithData(),
                    s.ordersMissing(), s.coveragePercent());
        }
        for (var cat : n.byCategory()) {
            for (var s : cat.nutrients()) {
                row(csv, cat.category(), s.nutrient(), s.unit(), s.total(), s.meanPerOrder(), s.meanPerServing(),
                        s.ordersWithData(), s.ordersMissing(), s.coveragePercent());
            }
        }
    }

    private void writeGeographic(OrderFilter filter, CSVPrinter csv) throws IOException {
        AnalyticsDtos.Geographic g = analyticsService.geographic(filter);
        csv.printRecord("city", "suppressed", "orders", "late_night_orders", "late_night_share_percent",
                "mean_kcal_per_late_night_order", "discretionary_share_percent");
        for (var c : g.cities()) {
            row(csv, c.city(), c.suppressed(), c.orders(), c.lateNightOrders(), c.lateNightSharePercent(),
                    c.meanKcalPerLateNightOrder(), c.discretionarySharePercent());
        }
    }

    private void writeSummary(OrderFilter filter, CSVPrinter csv) throws IOException {
        var o = analyticsService.overview(filter);
        Map<String, Object> meta = metadata(filter);
        csv.printRecord("section", "metric", "value");
        row(csv, "report", "title", "NutriNight AI - late-night food order summary");
        row(csv, "report", "generated_at", meta.get("generatedAt"));
        row(csv, "report", "period_from", Objects.toString(filter.from(), "all data"));
        row(csv, "report", "period_to", Objects.toString(filter.to(), "all data"));
        row(csv, "report", "data_first_day", o.scope().dataStart());
        row(csv, "report", "data_last_day", o.scope().dataEnd());
        row(csv, "report", "city_filter", Objects.toString(filter.city(), "all"));
        row(csv, "report", "late_night_window", o.window().start() + "-" + o.window().end() + " " + o.window().timezone());
        row(csv, "report", "data_sources", String.join("; ", o.dataSources()));
        row(csv, "overview", "total_orders", o.totalOrders());
        row(csv, "overview", "late_night_orders", o.lateNightOrders());
        row(csv, "overview", "late_night_percentage", o.lateNightPercentage());
        row(csv, "overview", "peak_hour", o.peakHour());
        row(csv, "overview", "peak_late_night_hour", o.peakLateNightHour());
        row(csv, "overview", "mean_kcal_per_late_night_order", o.meanKcalPerLateNightOrder());
        for (var c : o.topLateNightCategories()) {
            row(csv, "top_late_night_category", c.label(), c.orders() + " (" + c.sharePercent() + "%)");
        }
        for (var s : analyticsService.nutrition(filter.withLateNightOnly(true)).nutrients()) {
            row(csv, "late_night_nutrition_mean_per_order", s.label() + " (" + s.unit() + ")",
                    s.meanPerOrder() + " [coverage " + s.coveragePercent() + "%]");
        }
        for (String l : LIMITATIONS) {
            row(csv, "limitation", "", l);
        }
    }

    /** Writes a row, neutralising spreadsheet formula injection in text cells. */
    private static void row(CSVPrinter csv, Object... values) throws IOException {
        Object[] safe = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof String s && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
                v = "'" + s;
            } else if (v instanceof java.math.BigDecimal d) {
                v = d.toPlainString();
            }
            safe[i] = v == null ? "" : v;
        }
        csv.printRecord(safe);
    }
}
