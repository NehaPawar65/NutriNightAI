package com.nutrinight.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** Typed application configuration bound from the {@code nutrinight.*} properties. */
@Validated
@ConfigurationProperties(prefix = "nutrinight")
public record NutriNightProperties(
        @NotBlank String timezone,
        @Valid @NotNull LateNight lateNight,
        @Valid @NotNull Privacy privacy,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Security security,
        @Valid @NotNull Cors cors,
        @Valid @NotNull NutritionThresholds nutritionThresholds) {

    public record LateNight(@NotNull LocalTime start, @NotNull LocalTime end) {
    }

    public record Privacy(@Min(1) int minGroupSize) {
    }

    public record Upload(@Min(1) long maxFileSizeBytes, @Min(1) int maxRows,
                         @Min(1) int maxReportedErrors, @Min(0) int previewRows) {
    }

    public record Ai(@NotBlank String baseUrl, String apiKey, @NotNull Duration connectTimeout,
                     @NotNull Duration readTimeout, @Min(1) int maxAnalysisRecords) {
    }

    public record Security(@NotBlank String adminUsername, String adminPassword) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Low / high cut-offs per 100 g (food) or 100 ml (drinks). */
    public record NutritionThresholds(String source, String sourceUrl,
                                      @Valid @NotNull ThresholdSet food, @Valid @NotNull ThresholdSet drinks) {
    }

    public record ThresholdSet(@NotNull Range sugarG, @NotNull Range saturatedFatG, @NotNull Range sodiumMg) {
    }

    public record Range(double low, double high) {
    }
}
