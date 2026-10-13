package com.nutrinight.controller;

import com.nutrinight.client.AiServiceClient;
import com.nutrinight.dto.AiDtos;
import com.nutrinight.dto.PageResponse;
import com.nutrinight.service.AiAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI")
public class AiController {

    private final AiAnalysisService aiService;

    public AiController(AiAnalysisService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/classify")
    @Operation(summary = "Classify food descriptions (keyword rules first, trained model as fallback)")
    public AiServiceClient.ClassifyResponse classify(@Valid @RequestBody AiDtos.ClassifyRequest request) {
        return aiService.classify(request);
    }

    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run pattern analysis, dietary indicator and clustering on stored orders; results are saved")
    public AiDtos.AnalyzeResponse analyze(@Valid @RequestBody AiDtos.AnalyzeRequest request) {
        return aiService.analyze(request);
    }

    @GetMapping("/insights")
    @Operation(summary = "Saved analysis results, newest first (type = PATTERN_ANALYSIS or CLUSTERING)")
    public PageResponse<AiDtos.AnalysisResultDto> insights(@RequestParam(required = false) String type,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "5") int size) {
        return aiService.insights(type, page, size);
    }

    @GetMapping("/model-status")
    @Operation(summary = "AI service reachability, model information and recorded model runs")
    public AiDtos.ModelStatus modelStatus() {
        return aiService.modelStatus();
    }
}
