package com.nutrinight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An order line. {@code orderRef} is a shortened one-way hash; raw platform order ids are never stored.
 * Per-order nutrient values are per-serving values multiplied by quantity ({@code null} when unavailable).
 */
public record OrderDto(Long id, String orderRef, Instant orderTimestamp, LocalDateTime localOrderTime, int localHour,
                       LocalDate analysisDate, boolean lateNight, Long foodItemId, String foodItem, String category,
                       int quantity, String city, String dataSource, BigDecimal caloriesKcalPerOrder,
                       BigDecimal sugarGPerOrder, BigDecimal sodiumMgPerOrder, BigDecimal saturatedFatGPerOrder,
                       BigDecimal proteinGPerOrder) {
}
