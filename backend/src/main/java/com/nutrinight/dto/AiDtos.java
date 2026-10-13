package com.nutrinight.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request/response shapes for /api/ai/**. */
public final class AiDtos {

    private AiDtos() {
    }

    public record ClassifyRequest(
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 200) String> items,
            @Pattern(regexp = "auto|rules|model", message = "must be one of auto, rules, model") String method) {

        public String methodOrDefault() {
            return method == null ? "auto" : method;
        }
    }

    public record AnalyzeRequest(
            LocalDate from,
            LocalDate to,
            @Size(max = 100) String city,
            Boolean includeClustering,
            Map<String, @DecimalMin("0.0") @DecimalMax("10.0") Double> indicatorWeights) {
    }

    public record AnalysisResultDto(Long id, String analysisType, LocalDate periodStart, LocalDate periodEnd,
                                    JsonNode filters, JsonNode result, Instant createdAt) {
    }

    public record AnalyzeResponse(int recordCount, AnalysisResultDto patternAnalysis, AnalysisResultDto clustering,
                                  String clusteringError) {
    }

    public record ModelRunDto(Long id, String modelName, String modelVersion, JsonNode evaluationMetrics,
                              String datasetVersion, Instant createdAt) {
    }

    public record ModelStatus(boolean aiServiceReachable, String message, JsonNode modelInfo,
                              List<ModelRunDto> recordedRuns) {
    }
}
