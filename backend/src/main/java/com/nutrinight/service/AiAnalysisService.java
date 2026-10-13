package com.nutrinight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutrinight.client.AiServiceClient;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.domain.LateNightWindow;
import com.nutrinight.dto.AiDtos.AnalysisResultDto;
import com.nutrinight.dto.AiDtos.AnalyzeRequest;
import com.nutrinight.dto.AiDtos.AnalyzeResponse;
import com.nutrinight.dto.AiDtos.ClassifyRequest;
import com.nutrinight.dto.AiDtos.ModelRunDto;
import com.nutrinight.dto.AiDtos.ModelStatus;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.dto.PageResponse;
import com.nutrinight.entity.AnalysisResult;
import com.nutrinight.entity.ModelRun;
import com.nutrinight.exception.AiServiceException;
import com.nutrinight.exception.AiServiceUnavailableException;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.exception.UnprocessableDataException;
import com.nutrinight.repository.AnalysisResultRepository;
import com.nutrinight.repository.AnalyticsRepository;
import com.nutrinight.repository.ModelRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridges stored orders and the Python AI service: builds requests from the database, calls the service,
 * and persists the results. Credentials and the service URL stay server-side.
 */
@Service
public class AiAnalysisService {

    public static final String TYPE_PATTERN = "PATTERN_ANALYSIS";
    public static final String TYPE_CLUSTERING = "CLUSTERING";
    private static final Set<String> TYPES = Set.of(TYPE_PATTERN, TYPE_CLUSTERING);
    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiServiceClient client;
    private final AnalyticsRepository analyticsRepository;
    private final AnalysisResultRepository resultRepository;
    private final ModelRunRepository modelRunRepository;
    private final NutriNightProperties properties;
    private final LateNightWindow window;
    private final ObjectMapper mapper;

    public AiAnalysisService(AiServiceClient client, AnalyticsRepository analyticsRepository,
                             AnalysisResultRepository resultRepository, ModelRunRepository modelRunRepository,
                             NutriNightProperties properties, LateNightWindow window, ObjectMapper mapper) {
        this.client = client;
        this.analyticsRepository = analyticsRepository;
        this.resultRepository = resultRepository;
        this.modelRunRepository = modelRunRepository;
        this.properties = properties;
        this.window = window;
        this.mapper = mapper;
    }

    public AiServiceClient.ClassifyResponse classify(ClassifyRequest request) {
        return client.classify(request.items(), request.methodOrDefault());
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        OrderFilter filter = OrderFilter.of(request.from(), request.to(), request.city(), null, false);
        int max = properties.ai().maxAnalysisRecords();
        List<Map<String, Object>> records = new ArrayList<>();
        int count = analyticsRepository.forEachAnalysisRecord(filter, max + 1, r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("localDateTime", r.localDateTime().toString());
            m.put("analysisDate", r.analysisDate().toString());
            m.put("lateNight", r.lateNight());
            m.put("category", r.category());
            m.put("quantity", r.quantity());
            m.put("city", r.city());
            m.put("caloriesKcal", num(r.caloriesKcal()));
            m.put("sugarG", num(r.sugarG()));
            m.put("sodiumMg", num(r.sodiumMg()));
            m.put("saturatedFatG", num(r.saturatedFatG()));
            m.put("proteinG", num(r.proteinG()));
            records.add(m);
        });
        if (count == 0) {
            throw new UnprocessableDataException("No orders match the selected period and filters");
        }
        if (count > max) {
            throw new UnprocessableDataException("The selection contains more than " + max
                    + " orders; narrow the date range or filter by city");
        }

        Map<String, Object> windowCfg = Map.of("start", window.start().toString(), "end", window.end().toString());
        Map<String, Object> analyzeBody = new LinkedHashMap<>();
        analyzeBody.put("records", records);
        analyzeBody.put("lateNightWindow", windowCfg);
        if (request.indicatorWeights() != null && !request.indicatorWeights().isEmpty()) {
            analyzeBody.put("indicatorWeights", request.indicatorWeights());
        }
        JsonNode analysis = client.analyze(analyzeBody);
        String filtersJson = toJson(request);
        AnalysisResult saved = resultRepository.save(new AnalysisResult(TYPE_PATTERN, firstDate(analysis, "periodStart"),
                firstDate(analysis, "periodEnd"), filtersJson, toJson(analysis)));

        AnalysisResultDto clusteringDto = null;
        String clusteringError = null;
        if (!Boolean.FALSE.equals(request.includeClustering())) {
            try {
                JsonNode clustering = client.cluster(Map.of("records", records, "lateNightWindow", windowCfg,
                        "lateNightOnly", true));
                AnalysisResult c = resultRepository.save(new AnalysisResult(TYPE_CLUSTERING, saved.getPeriodStart(),
                        saved.getPeriodEnd(), filtersJson, toJson(clustering)));
                clusteringDto = toDto(c);
            } catch (AiServiceException | AiServiceUnavailableException e) {
                // Pattern analysis is still useful without the optional clustering step.
                log.warn("Clustering failed: {}", e.getMessage());
                clusteringError = e.getMessage();
            }
        }
        log.info("Stored AI analysis {} over {} orders", saved.getId(), count);
        return new AnalyzeResponse(count, toDto(saved), clusteringDto, clusteringError);
    }

    public PageResponse<AnalysisResultDto> insights(String type, int page, int size) {
        if (size < 1 || size > 50 || page < 0) {
            throw new InvalidRequestException("'page' must be >= 0 and 'size' between 1 and 50");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        if (type == null || type.isBlank()) {
            return PageResponse.of(resultRepository.findAll(pageable), this::toDto);
        }
        if (!TYPES.contains(type)) {
            throw new InvalidRequestException("'type' must be one of " + TYPES);
        }
        return PageResponse.of(resultRepository.findByAnalysisType(type, pageable), this::toDto);
    }

    /** Reports model status; also records the deployed model version in model_runs the first time it is seen. */
    public ModelStatus modelStatus() {
        JsonNode info = null;
        String message;
        boolean reachable;
        try {
            info = client.modelInfo();
            reachable = true;
            message = "AI service reachable";
            registerModelRun(info.path("classifier"));
        } catch (AiServiceUnavailableException | AiServiceException e) {
            reachable = false;
            message = e.getMessage();
        }
        List<ModelRunDto> runs = modelRunRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(r -> new ModelRunDto(r.getId(), r.getModelName(), r.getModelVersion(), readJson(r.getEvaluationMetrics()),
                        r.getDatasetVersion(), r.getCreatedAt()))
                .toList();
        return new ModelStatus(reachable, message, info, runs);
    }

    public boolean isReachable() {
        try {
            client.health();
            return true;
        } catch (AiServiceUnavailableException | AiServiceException e) {
            return false;
        }
    }

    private void registerModelRun(JsonNode classifier) {
        if (!classifier.path("available").asBoolean(false)) {
            return;
        }
        String name = classifier.path("modelName").asText(null);
        String version = classifier.path("modelVersion").asText(null);
        if (name == null || version == null || modelRunRepository.findByModelNameAndModelVersion(name, version).isPresent()) {
            return;
        }
        try {
            modelRunRepository.save(new ModelRun(name, version, toJson(classifier.path("metrics")),
                    classifier.path("datasetVersion").asText(null)));
            log.info("Recorded model run {} {}", name, version);
        } catch (DataIntegrityViolationException e) {
            log.debug("Model run {} {} was recorded concurrently", name, version);
        }
    }

    private AnalysisResultDto toDto(AnalysisResult r) {
        return new AnalysisResultDto(r.getId(), r.getAnalysisType(), r.getPeriodStart(), r.getPeriodEnd(),
                readJson(r.getFiltersJson()), readJson(r.getResultJson()), r.getCreatedAt());
    }

    private static java.time.LocalDate firstDate(JsonNode analysis, String field) {
        String text = analysis.path("summary").path(field).asText(null);
        return text == null ? null : java.time.LocalDate.parse(text);
    }

    private static Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise analysis data", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored analysis JSON is invalid", e);
        }
    }
}
