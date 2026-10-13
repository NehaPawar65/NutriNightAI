package com.nutrinight.service.csv;

import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.OrderTimeResolver;
import com.nutrinight.domain.OrderTimeResolver.ResolvedOrderTime;
import com.nutrinight.exception.InvalidRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates an order CSV. Invalid rows are reported with row numbers and skipped;
 * columns that could contain personal data are never read.
 */
public class CsvOrderParser {

    public static final List<String> REQUIRED_COLUMNS = List.of("order_id", "order_time", "food_item", "quantity", "city");
    public static final List<String> OPTIONAL_COLUMNS = List.of("food_category", "serving_size_g", "serving_size",
            "serving_unit", "calories_kcal", "sugar_g", "sodium_mg", "saturated_fat_g", "protein_g",
            "data_source", "nutrition_source", "nutrition_source_url");

    /** Columns that may identify a person. They are ignored and reported as a warning. */
    static final Set<String> PERSONAL_DATA_COLUMNS = Set.of("name", "customer", "customer_name", "first_name",
            "last_name", "phone", "phone_number", "mobile", "email", "address", "home_address", "street",
            "latitude", "longitude", "lat", "lon", "lng", "pincode", "postcode", "zip", "zipcode",
            "customer_id", "user_id", "user_name", "username");

    private static final Map<String, BigDecimal> NUTRIENT_MAX = Map.of(
            "calories_kcal", new BigDecimal("5000"),
            "sugar_g", new BigDecimal("500"),
            "sodium_mg", new BigDecimal("20000"),
            "saturated_fat_g", new BigDecimal("300"),
            "protein_g", new BigDecimal("300"));
    private static final BigDecimal MAX_SERVING = new BigDecimal("5000");
    private static final int MAX_QUANTITY = 100;

    private final OrderTimeResolver timeResolver;
    private final int maxRows;
    private final int maxReportedErrors;

    public CsvOrderParser(OrderTimeResolver timeResolver, int maxRows, int maxReportedErrors) {
        this.timeResolver = timeResolver;
        this.maxRows = maxRows;
        this.maxReportedErrors = maxReportedErrors;
    }

    public ParseResult parse(InputStream input) {
        try (Reader reader = stripBom(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .setAllowMissingColumnNames(true)
                     .get()
                     .parse(reader)) {
            Map<String, Integer> headerIndex = indexHeader(parser.getHeaderNames());
            List<String> warnings = new ArrayList<>();
            checkColumns(headerIndex, warnings);

            List<ParsedOrderRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            int total = 0;
            int invalid = 0;
            int duplicatesInFile = 0;

            for (CSVRecord record : parser) {
                total++;
                if (total > maxRows) {
                    throw new InvalidRequestException("The file has more than the maximum of " + maxRows + " data rows");
                }
                int rowNumber = (int) record.getRecordNumber() + 1; // +1 for the header line
                List<RowError> rowErrors = new ArrayList<>();
                ParsedOrderRow row = parseRow(record, headerIndex, rowNumber, rowErrors);
                if (!rowErrors.isEmpty()) {
                    invalid++;
                    for (RowError e : rowErrors) {
                        if (errors.size() < maxReportedErrors) {
                            errors.add(e);
                        }
                    }
                    continue;
                }
                if (!seenIds.add(row.externalOrderId())) {
                    duplicatesInFile++;
                    continue;
                }
                rows.add(row);
            }
            if (total == 0) {
                throw new InvalidRequestException("The CSV file contains a header but no data rows");
            }
            if (invalid > errors.size()) {
                warnings.add("Only the first " + errors.size() + " row errors are listed (" + invalid + " invalid rows in total)");
            }
            return new ParseResult(total, rows, invalid, duplicatesInFile, errors, warnings);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded CSV", e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Commons CSV raises these for malformed structure such as unbalanced quotes or duplicate headers.
            throw new InvalidRequestException("The file is not a valid CSV: " + e.getMessage());
        } catch (UncheckedIOException e) {
            throw new InvalidRequestException("The file is not a valid CSV: " + e.getMessage());
        }
    }

    private static Reader stripBom(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != '﻿') {
            reader.reset();
        }
        return reader;
    }

    private static Map<String, Integer> indexHeader(List<String> headerNames) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerNames.size(); i++) {
            String key = headerNames.get(i) == null ? "" : headerNames.get(i).trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[\\s-]+", "_");
            if (!key.isEmpty()) {
                index.putIfAbsent(key, i);
            }
        }
        return index;
    }

    private static void checkColumns(Map<String, Integer> header, List<String> warnings) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("Missing required column(s): " + String.join(", ", missing)
                    + ". Required: " + String.join(", ", REQUIRED_COLUMNS));
        }
        Set<String> personal = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (String column : header.keySet()) {
            if (PERSONAL_DATA_COLUMNS.contains(column)) {
                personal.add(column);
            } else if (!REQUIRED_COLUMNS.contains(column) && !OPTIONAL_COLUMNS.contains(column)
                    && !"food_category".equals(column)) {
                unknown.add(column);
            }
        }
        if (!personal.isEmpty()) {
            warnings.add("Columns that may contain personal data were ignored and not stored: " + String.join(", ", personal));
        }
        if (!unknown.isEmpty()) {
            warnings.add("Unrecognised columns were ignored: " + String.join(", ", unknown));
        }
    }

    private ParsedOrderRow parseRow(CSVRecord record, Map<String, Integer> header, int row, List<RowError> errors) {
        String orderId = required(record, header, "order_id", 100, row, errors);
        String foodName = required(record, header, "food_item", 200, row, errors);
        String city = required(record, header, "city", 100, row, errors);
        String dataSource = optional(record, header, "data_source");
        if (dataSource == null) {
            dataSource = "UPLOAD";
        } else if (dataSource.length() > 50) {
            errors.add(new RowError(row, "data_source", "must be at most 50 characters"));
        }

        ResolvedOrderTime time = null;
        try {
            time = timeResolver.resolve(optional(record, header, "order_time"));
        } catch (IllegalArgumentException e) {
            errors.add(new RowError(row, "order_time", e.getMessage()));
        }

        Integer quantity = null;
        String qtyText = optional(record, header, "quantity");
        if (qtyText == null) {
            errors.add(new RowError(row, "quantity", "is required"));
        } else {
            try {
                quantity = Integer.parseInt(qtyText);
                if (quantity < 1 || quantity > MAX_QUANTITY) {
                    errors.add(new RowError(row, "quantity", "must be between 1 and " + MAX_QUANTITY));
                }
            } catch (NumberFormatException e) {
                errors.add(new RowError(row, "quantity", "must be a whole number"));
            }
        }

        FoodCategory category = null;
        String categoryText = optional(record, header, "food_category");
        if (categoryText != null) {
            category = FoodCategory.parse(categoryText).orElse(null);
            if (category == null) {
                errors.add(new RowError(row, "food_category", "unknown category '" + categoryText + "'"));
            }
        }

        String servingColumn = header.containsKey("serving_size_g") ? "serving_size_g" : "serving_size";
        BigDecimal servingSize = decimal(record, header, servingColumn, MAX_SERVING, row, errors);
        if (servingSize != null && servingSize.signum() == 0) {
            errors.add(new RowError(row, servingColumn, "must be greater than 0"));
        }
        String unit = optional(record, header, "serving_unit");
        if (unit != null) {
            unit = unit.toLowerCase(Locale.ROOT);
            if (!unit.equals("g") && !unit.equals("ml")) {
                errors.add(new RowError(row, "serving_unit", "must be 'g' or 'ml'"));
            }
        } else if (servingSize != null) {
            unit = "g";
        }

        BigDecimal kcal = decimal(record, header, "calories_kcal", NUTRIENT_MAX.get("calories_kcal"), row, errors);
        BigDecimal sugar = decimal(record, header, "sugar_g", NUTRIENT_MAX.get("sugar_g"), row, errors);
        BigDecimal sodium = decimal(record, header, "sodium_mg", NUTRIENT_MAX.get("sodium_mg"), row, errors);
        BigDecimal satFat = decimal(record, header, "saturated_fat_g", NUTRIENT_MAX.get("saturated_fat_g"), row, errors);
        BigDecimal protein = decimal(record, header, "protein_g", NUTRIENT_MAX.get("protein_g"), row, errors);

        if (!errors.isEmpty()) {
            return null;
        }
        return new ParsedOrderRow(row, hashOrderId(dataSource, orderId), time, foodName.replaceAll("\\s+", " "),
                category, servingSize, unit, kcal, sugar, sodium, satFat, protein, quantity, normalizeCity(city),
                dataSource.toUpperCase(Locale.ROOT), optional(record, header, "nutrition_source"),
                optional(record, header, "nutrition_source_url"));
    }

    private static String optional(CSVRecord record, Map<String, Integer> header, String column) {
        Integer idx = header.get(column);
        if (idx == null || idx >= record.size()) {
            return null;
        }
        String value = record.get(idx);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(CSVRecord record, Map<String, Integer> header, String column, int maxLength,
                                   int row, List<RowError> errors) {
        String value = optional(record, header, column);
        if (value == null) {
            errors.add(new RowError(row, column, "is required"));
            return null;
        }
        if (value.length() > maxLength) {
            errors.add(new RowError(row, column, "must be at most " + maxLength + " characters"));
        }
        return value;
    }

    private static BigDecimal decimal(CSVRecord record, Map<String, Integer> header, String column, BigDecimal max,
                                      int row, List<RowError> errors) {
        String text = optional(record, header, column);
        if (text == null) {
            return null; // missing is kept distinct from zero
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() < 0) {
                errors.add(new RowError(row, column, "must not be negative"));
            } else if (value.compareTo(max) > 0) {
                errors.add(new RowError(row, column, "is implausibly large (max " + max.toPlainString() + ")"));
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(new RowError(row, column, "must be a number"));
            return null;
        }
    }

    static String normalizeCity(String city) {
        String[] words = city.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** One-way SHA-256 hash so raw platform order ids are never stored. */
    static String hashOrderId(String dataSource, String orderId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((dataSource.toUpperCase(Locale.ROOT) + ":" + orderId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RowError(int row, String column, String message) {
    }

    public record ParsedOrderRow(int rowNumber, String externalOrderId, ResolvedOrderTime time, String foodName,
                                 FoodCategory category, BigDecimal servingSize, String servingUnit,
                                 BigDecimal caloriesKcal, BigDecimal sugarG, BigDecimal sodiumMg,
                                 BigDecimal saturatedFatG, BigDecimal proteinG, int quantity, String city,
                                 String dataSource, String nutritionSource, String nutritionSourceUrl) {

        public boolean hasAnyNutrient() {
            return caloriesKcal != null || sugarG != null || sodiumMg != null || saturatedFatG != null || proteinG != null;
        }
    }

    public record ParseResult(int totalRows, List<ParsedOrderRow> validRows, int invalidRows, int duplicatesInFile,
                              List<RowError> errors, List<String> warnings) {
    }
}
