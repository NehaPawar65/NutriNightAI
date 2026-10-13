package com.nutrinight.controller;

import com.nutrinight.dto.ComparisonDto;
import com.nutrinight.dto.FoodItemDto;
import com.nutrinight.dto.RecommendationGroupDto;
import com.nutrinight.mapper.FoodItemMapper;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final FoodItemRepository foodItemRepository;

    public RecommendationController(RecommendationService recommendationService, FoodItemRepository foodItemRepository) {
        this.recommendationService = recommendationService;
        this.foodItemRepository = foodItemRepository;
    }

    @GetMapping("/api/recommendations")
    @Operation(summary = "Healthier alternatives based on available nutrition data (optionally for one food item)")
    public List<RecommendationGroupDto> recommendations(@RequestParam(required = false) Long foodItemId) {
        return recommendationService.list(foodItemId);
    }

    @GetMapping("/api/recommendations/compare")
    @Operation(summary = "Compare two food items per serving and per 100 g/ml")
    public ComparisonDto compare(@RequestParam Long sourceId, @RequestParam Long alternativeId) {
        return recommendationService.compare(sourceId, alternativeId);
    }

    @GetMapping("/api/food-items")
    @Transactional(readOnly = true)
    @Operation(summary = "All food items with per-serving nutrition and source")
    public List<FoodItemDto> foodItems() {
        return foodItemRepository.findAllByOrderByNameAsc().stream().map(FoodItemMapper::toDto).toList();
    }
}
