package com.nutrinight.controller;

import com.nutrinight.dto.AnalyticsDtos;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Date filters refer to analysis days (04:00-04:00 local time). */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Dashboard totals: orders, late-night share, peak hours, top late-night categories")
    public AnalyticsDtos.Overview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category) {
        return analyticsService.overview(OrderFilter.of(from, to, city, category, false));
    }

    @GetMapping("/hourly")
    @Operation(summary = "Order volume by local hour with weekday/weekend comparison")
    public AnalyticsDtos.Hourly hourly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category) {
        return analyticsService.hourly(OrderFilter.of(from, to, city, category, false));
    }

    @GetMapping("/categories")
    @Operation(summary = "Food-category distribution (late-night orders by default)")
    public AnalyticsDtos.Categories categories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "true") boolean lateNightOnly) {
        return analyticsService.categories(OrderFilter.of(from, to, city, category, lateNightOnly));
    }

    @GetMapping("/nutrition")
    @Operation(summary = "Nutrient totals, means per order and per serving, coverage and level distribution")
    public AnalyticsDtos.Nutrition nutrition(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "true") boolean lateNightOnly) {
        return analyticsService.nutrition(OrderFilter.of(from, to, city, category, lateNightOnly));
    }

    @GetMapping("/geographic")
    @Operation(summary = "City-level summary with small-group suppression")
    public AnalyticsDtos.Geographic geographic(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category) {
        return analyticsService.geographic(OrderFilter.of(from, to, null, category, false));
    }

    @GetMapping("/filters")
    @Operation(summary = "Available filter values (cities, categories, date range)")
    public AnalyticsDtos.FilterOptions filters() {
        return analyticsService.filterOptions();
    }
}
