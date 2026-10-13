package com.nutrinight.dto;

import com.nutrinight.service.csv.CsvOrderParser.RowError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Outcome of a CSV upload (or a dry-run validation). */
public record ImportResultDto(
        boolean dryRun,
        String fileName,
        Long batchId,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRowsInFile,
        int duplicateRowsInDatabase,
        int importedRows,
        int lateNightRows,
        int newFoodItems,
        int foodItemsClassifiedByAi,
        List<RowError> errors,
        List<String> warnings,
        List<PreviewRow> preview) {

    public record PreviewRow(int row, LocalDateTime localOrderTime, LocalDate analysisDate, boolean lateNight,
                             String foodItem, String category, int quantity, String city,
                             BigDecimal caloriesKcal, BigDecimal sugarG, BigDecimal sodiumMg,
                             BigDecimal saturatedFatG, BigDecimal proteinG, String dataSource) {
    }
}
