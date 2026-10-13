package com.nutrinight.service;

import com.nutrinight.domain.CategorySource;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.entity.ImportBatch;
import com.nutrinight.entity.NutritionSource;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.repository.ImportBatchRepository;
import com.nutrinight.repository.NutritionSourceRepository;
import com.nutrinight.service.csv.CsvOrderParser.ParsedOrderRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes one validated import in a single transaction: either everything is stored or nothing is. */
@Component
public class OrderImportWriter {

    private static final int BATCH_SIZE = 1000;

    private final FoodItemRepository foodItemRepository;
    private final NutritionSourceRepository nutritionSourceRepository;
    private final ImportBatchRepository importBatchRepository;
    private final JdbcTemplate jdbcTemplate;

    public OrderImportWriter(FoodItemRepository foodItemRepository, NutritionSourceRepository nutritionSourceRepository,
                             ImportBatchRepository importBatchRepository, JdbcTemplate jdbcTemplate) {
        this.foodItemRepository = foodItemRepository;
        this.nutritionSourceRepository = nutritionSourceRepository;
        this.importBatchRepository = importBatchRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** A food item to create, defined by the first row that mentions it. */
    public record NewFoodItem(ParsedOrderRow definingRow, FoodCategory category, CategorySource categorySource) {
    }

    /**
     * @param existingItems food items already in the database, keyed by normalised name
     * @param newItems      food items to create, keyed by normalised name
     */
    @Transactional
    public ImportBatch write(String fileName, int totalRows, int invalidRows, int duplicateRows,
                             Map<String, FoodItem> existingItems, Map<String, NewFoodItem> newItems,
                             List<ParsedOrderRow> rows) {
        ImportBatch batch = importBatchRepository.save(new ImportBatch(fileName, totalRows, invalidRows, duplicateRows));

        Map<String, FoodItem> items = new HashMap<>(existingItems);
        Map<String, NutritionSource> sources = new HashMap<>();
        for (Map.Entry<String, NewFoodItem> entry : newItems.entrySet()) {
            ParsedOrderRow r = entry.getValue().definingRow();
            FoodItem item = new FoodItem(r.foodName(), entry.getValue().category(), entry.getValue().categorySource());
            item.setServing(r.servingSize(), r.servingUnit());
            item.setNutrients(r.caloriesKcal(), r.sugarG(), r.sodiumMg(), r.saturatedFatG(), r.proteinG());
            if (r.nutritionSource() != null && r.hasAnyNutrient()) {
                item.setNutritionSource(sources.computeIfAbsent(r.nutritionSource(), name ->
                        nutritionSourceRepository.findBySourceName(name).orElseGet(() ->
                                nutritionSourceRepository.save(new NutritionSource(name, r.nutritionSourceUrl(),
                                        "Registered automatically from CSV import")))));
            }
            items.put(entry.getKey(), foodItemRepository.save(item));
        }
        foodItemRepository.flush();

        String sql = """
                INSERT INTO food_orders (external_order_id, order_timestamp, local_date_time, local_hour, analysis_date,
                    is_late_night, food_item_id, quantity, city, data_source, import_batch_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<ParsedOrderRow> chunk = rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE));
            jdbcTemplate.batchUpdate(sql, chunk, chunk.size(), (ps, r) -> {
                ps.setString(1, r.externalOrderId());
                ps.setObject(2, java.time.OffsetDateTime.ofInstant(r.time().instant(), java.time.ZoneOffset.UTC));
                ps.setTimestamp(3, Timestamp.valueOf(r.time().localDateTime()));
                ps.setShort(4, (short) r.time().localHour());
                ps.setDate(5, Date.valueOf(r.time().analysisDate()));
                ps.setBoolean(6, r.time().lateNight());
                ps.setLong(7, items.get(FoodItem.normalize(r.foodName())).getId());
                ps.setInt(8, r.quantity());
                ps.setString(9, r.city());
                ps.setString(10, r.dataSource());
                ps.setLong(11, batch.getId());
            });
        }
        batch.complete(rows.size(), newItems.size());
        return importBatchRepository.save(batch);
    }
}
