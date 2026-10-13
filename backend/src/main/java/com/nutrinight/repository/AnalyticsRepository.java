package com.nutrinight.repository;

import com.nutrinight.dto.OrderFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Read-only aggregate SQL for analytics. All user input is bound as parameters. */
@Repository
public class AnalyticsRepository {

    private static final String WEEKEND_EXPR = "EXTRACT(ISODOW FROM o.analysis_date) IN (6, 7)";

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Order counts grouped by local hour, weekend flag, late-night flag and food item. */
    public record Cell(int hour, boolean weekend, boolean lateNight, long foodItemId, long orders, long quantity) {
    }

    public record CityCell(String city, boolean lateNight, long foodItemId, long orders, long quantity) {
    }

    public record DataRange(LocalDate minDate, LocalDate maxDate, long weekdayDays, long weekendDays) {
    }

    public record AnalysisRecord(LocalDateTime localDateTime, LocalDate analysisDate, boolean lateNight,
                                 String category, int quantity, String city, BigDecimal caloriesKcal,
                                 BigDecimal sugarG, BigDecimal sodiumMg, BigDecimal saturatedFatG,
                                 BigDecimal proteinG) {
    }

    public List<Cell> cells(OrderFilter filter) {
        Where w = where(filter);
        String sql = "SELECT o.local_hour, " + WEEKEND_EXPR + " AS weekend, o.is_late_night, o.food_item_id, "
                + "COUNT(*) AS orders, SUM(o.quantity) AS qty FROM food_orders o JOIN food_items f ON f.id = o.food_item_id"
                + w.sql + " GROUP BY 1, 2, 3, 4";
        return jdbc.query(sql, w.params, (rs, i) -> new Cell(rs.getInt(1), rs.getBoolean(2), rs.getBoolean(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6)));
    }

    public List<CityCell> cityCells(OrderFilter filter) {
        Where w = where(filter);
        String sql = "SELECT o.city, o.is_late_night, o.food_item_id, COUNT(*), SUM(o.quantity) "
                + "FROM food_orders o JOIN food_items f ON f.id = o.food_item_id" + w.sql + " GROUP BY 1, 2, 3";
        return jdbc.query(sql, w.params, (rs, i) -> new CityCell(rs.getString(1), rs.getBoolean(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5)));
    }

    /** Date range and number of distinct weekday / weekend analysis days in the filtered data. */
    public DataRange range(OrderFilter filter) {
        Where w = where(filter);
        String sql = "SELECT MIN(o.analysis_date), MAX(o.analysis_date), "
                + "COUNT(DISTINCT o.analysis_date) FILTER (WHERE NOT (" + WEEKEND_EXPR + ")), "
                + "COUNT(DISTINCT o.analysis_date) FILTER (WHERE " + WEEKEND_EXPR + ") "
                + "FROM food_orders o JOIN food_items f ON f.id = o.food_item_id" + w.sql;
        return jdbc.queryForObject(sql, w.params, (rs, i) -> new DataRange(date(rs, 1), date(rs, 2),
                rs.getLong(3), rs.getLong(4)));
    }

    public List<String> distinctCities() {
        return jdbc.queryForList("SELECT DISTINCT city FROM food_orders ORDER BY city", new MapSqlParameterSource(),
                String.class);
    }

    public List<String> distinctDataSources(OrderFilter filter) {
        Where w = where(filter);
        return jdbc.queryForList("SELECT DISTINCT o.data_source FROM food_orders o JOIN food_items f ON f.id = o.food_item_id"
                + w.sql + " ORDER BY 1", w.params, String.class);
    }

    /** Streams order records with per-serving nutrients, oldest first, up to {@code limit} rows. */
    public int forEachAnalysisRecord(OrderFilter filter, int limit, Consumer<AnalysisRecord> consumer) {
        Where w = where(filter);
        w.params.addValue("limit", limit);
        String sql = "SELECT o.local_date_time, o.analysis_date, o.is_late_night, f.category, o.quantity, o.city, "
                + "f.calories_kcal, f.sugar_g, f.sodium_mg, f.saturated_fat_g, f.protein_g "
                + "FROM food_orders o JOIN food_items f ON f.id = o.food_item_id" + w.sql
                + " ORDER BY o.local_date_time, o.id LIMIT :limit";
        List<Integer> count = new ArrayList<>(List.of(0));
        jdbc.query(sql, w.params, rs -> {
            consumer.accept(new AnalysisRecord(rs.getTimestamp(1).toLocalDateTime(), rs.getDate(2).toLocalDate(),
                    rs.getBoolean(3), rs.getString(4), rs.getInt(5), rs.getString(6), rs.getBigDecimal(7),
                    rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11)));
            count.set(0, count.get(0) + 1);
        });
        return count.get(0);
    }

    private record Where(String sql, MapSqlParameterSource params) {
    }

    private static Where where(OrderFilter f) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (f.from() != null) {
            clauses.add("o.analysis_date >= :from");
            p.addValue("from", Date.valueOf(f.from()));
        }
        if (f.to() != null) {
            clauses.add("o.analysis_date <= :to");
            p.addValue("to", Date.valueOf(f.to()));
        }
        if (f.city() != null) {
            clauses.add("LOWER(o.city) = :city");
            p.addValue("city", f.city().toLowerCase(Locale.ROOT));
        }
        if (f.category() != null) {
            clauses.add("f.category = :category");
            p.addValue("category", f.category().name());
        }
        if (Boolean.TRUE.equals(f.lateNightOnly())) {
            clauses.add("o.is_late_night = TRUE");
        }
        return new Where(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), p);
    }

    private static LocalDate date(ResultSet rs, int col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }
}
