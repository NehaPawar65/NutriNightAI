package com.nutrinight.dto;

import com.nutrinight.domain.FoodCategory;
import com.nutrinight.exception.InvalidRequestException;

import java.time.LocalDate;

/**
 * Common filters. Dates refer to the analysis day (04:00 to 04:00 local time), so an order at 01:00 on the 2nd
 * belongs to the 1st and is included when {@code to} is the 1st.
 */
public record OrderFilter(LocalDate from, LocalDate to, String city, FoodCategory category, Boolean lateNightOnly,
                          String search) {

    public OrderFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        city = blankToNull(city);
        search = blankToNull(search);
        if (search != null && search.length() > 100) {
            throw new InvalidRequestException("'search' must be at most 100 characters");
        }
    }

    public static OrderFilter of(LocalDate from, LocalDate to, String city, String category, Boolean lateNightOnly) {
        return of(from, to, city, category, lateNightOnly, null);
    }

    public static OrderFilter of(LocalDate from, LocalDate to, String city, String category, Boolean lateNightOnly,
                                 String search) {
        FoodCategory cat = null;
        if (category != null && !category.isBlank()) {
            cat = FoodCategory.parse(category)
                    .orElseThrow(() -> new InvalidRequestException("Unknown category '" + category + "'"));
        }
        return new OrderFilter(from, to, city, cat, lateNightOnly, search);
    }

    public OrderFilter withLateNightOnly(Boolean value) {
        return new OrderFilter(from, to, city, category, value, search);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
