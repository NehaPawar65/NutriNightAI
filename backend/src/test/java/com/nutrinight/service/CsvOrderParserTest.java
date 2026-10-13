package com.nutrinight.service;

import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.LateNightWindow;
import com.nutrinight.domain.OrderTimeResolver;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.service.csv.CsvOrderParser;
import com.nutrinight.service.csv.CsvOrderParser.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvOrderParserTest {

    private static final String HEADER = "order_id,order_time,food_item,food_category,quantity,serving_size_g,"
            + "serving_unit,calories_kcal,sugar_g,sodium_mg,saturated_fat_g,protein_g,city,data_source\n";

    private final CsvOrderParser parser = new CsvOrderParser(new OrderTimeResolver(ZoneId.of("Asia/Kolkata"),
            new LateNightWindow(LocalTime.of(22, 0), LocalTime.of(4, 0))), 1000, 50);

    private ParseResult parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesValidRowsAndComputesLateNightFields() {
        ParseResult result = parse(HEADER
                + "A1,2026-07-01T23:15:00,Vada Pav,FRIED_SNACKS,2,150,g,290,3,560,3.5,7,mumbai,SYNTHETIC\n"
                + "A2,2026-07-02T04:00:00,Fruit Bowl,Fruits and vegetables,1,250,g,140,25,10,0.2,2,New  delhi,SYNTHETIC\n");

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.validRows()).hasSize(2);
        assertThat(result.errors()).isEmpty();
        var first = result.validRows().get(0);
        assertThat(first.time().lateNight()).isTrue();
        assertThat(first.city()).isEqualTo("Mumbai");
        assertThat(first.category()).isEqualTo(FoodCategory.FRIED_SNACKS);
        assertThat(first.externalOrderId()).hasSize(64).doesNotContain("A1");
        var second = result.validRows().get(1);
        assertThat(second.time().lateNight()).isFalse();
        assertThat(second.category()).isEqualTo(FoodCategory.FRUITS_VEGETABLES);
        assertThat(second.city()).isEqualTo("New Delhi");
    }

    @Test
    void keepsMissingNutrientsDistinctFromZero() {
        ParseResult result = parse(HEADER + "A1,2026-07-01T23:15:00,Cola,,1,500,ml,210,,20,0,0,Pune,\n");
        var row = result.validRows().get(0);
        assertThat(row.sugarG()).isNull();
        assertThat(row.saturatedFatG()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.category()).isNull();
        assertThat(row.dataSource()).isEqualTo("UPLOAD");
    }

    @Test
    void reportsRowErrorsWithRowNumbers() {
        ParseResult result = parse(HEADER
                + "A1,not-a-date,Vada Pav,,2,,,,,,,,Mumbai,\n"
                + "A2,2026-07-01T23:15:00,,,0,,,,,,,,Mumbai,\n"
                + "A3,2026-07-01T23:15:00,Pizza,Junk food,1,,,-5,,,,,Mumbai,\n"
                + "A4,2026-07-01T23:15:00,Pizza,,1,,,abc,,,,,Mumbai,\n"
                + "A5,2026-07-01T23:15:00,Pizza,,1,,,500,,,,,Mumbai,\n");

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.invalidRows()).isEqualTo(4);
        assertThat(result.errors()).extracting(CsvOrderParser.RowError::row).contains(2, 3, 4, 5);
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.column()).isEqualTo("order_time");
        });
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.column()).isEqualTo("food_item"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.column()).isEqualTo("quantity"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("unknown category"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("must not be negative"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("must be a number"));
    }

    @Test
    void skipsDuplicateOrderIdsWithinFile() {
        ParseResult result = parse(HEADER
                + "A1,2026-07-01T23:15:00,Pizza,,1,,,,,,,,Mumbai,\n"
                + "A1,2026-07-01T23:16:00,Pizza,,1,,,,,,,,Mumbai,\n");
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.duplicatesInFile()).isEqualTo(1);
    }

    @Test
    void rejectsMissingRequiredColumns() {
        assertThatThrownBy(() -> parse("order_id,food_item\nA1,Pizza\n"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("order_time")
                .hasMessageContaining("quantity")
                .hasMessageContaining("city");
    }

    @Test
    void rejectsHeaderOnlyFile() {
        assertThatThrownBy(() -> parse(HEADER)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    void ignoresPersonalDataColumnsWithWarning() {
        ParseResult result = parse("order_id,order_time,food_item,quantity,city,customer_name,phone\n"
                + "A1,2026-07-01T23:15:00,Pizza,1,Pune,Jane Doe,9999999999\n");
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("personal data").contains("customer_name"));
        assertThat(result.validRows().get(0).toString()).doesNotContain("Jane").doesNotContain("9999999999");
    }

    @Test
    void handlesUtf8BomAndHeaderVariants() {
        ParseResult result = parse("﻿Order ID,Order Time,Food Item,Quantity,City\nA1,2026-07-01 22:00,Samosa,1,Pune\n");
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.validRows().get(0).time().lateNight()).isTrue();
    }

    @Test
    void enforcesMaximumRowCount() {
        CsvOrderParser small = new CsvOrderParser(new OrderTimeResolver(ZoneId.of("UTC"),
                new LateNightWindow(LocalTime.of(22, 0), LocalTime.of(4, 0))), 1, 10);
        String csv = HEADER + "A1,2026-07-01T23:15:00,Pizza,,1,,,,,,,,Mumbai,\nA2,2026-07-01T23:15:00,Pizza,,1,,,,,,,,Mumbai,\n";
        assertThatThrownBy(() -> small.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("maximum");
    }

    @Test
    void rejectsMalformedCsv() {
        assertThatThrownBy(() -> parse(HEADER + "A1,\"2026-07-01T23:15:00,Pizza,,1\n"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void sampleDatasetParsesWithoutErrors() throws Exception {
        var path = java.nio.file.Path.of("..", "data", "sample_food_orders.csv");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        CsvOrderParser big = new CsvOrderParser(new OrderTimeResolver(ZoneId.of("Asia/Kolkata"),
                new LateNightWindow(LocalTime.of(22, 0), LocalTime.of(4, 0))), 200_000, 50);
        try (var in = java.nio.file.Files.newInputStream(path)) {
            ParseResult result = big.parse(in);
            assertThat(result.errors()).isEmpty();
            assertThat(result.validRows()).hasSize(result.totalRows());
        }
    }
}
