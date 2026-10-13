package com.nutrinight.service;

import com.nutrinight.dto.ComparisonDto;
import com.nutrinight.dto.RecommendationGroupDto;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.entity.Recommendation;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.exception.ResourceNotFoundException;
import com.nutrinight.mapper.FoodItemMapper;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Finds healthier alternatives among the food items that have nutrition data. */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    static final int MAX_ALTERNATIVES = 3;

    private final FoodItemRepository foodItemRepository;
    private final RecommendationRepository recommendationRepository;
    private final NutrientComparator comparator;

    public RecommendationService(FoodItemRepository foodItemRepository,
                                 RecommendationRepository recommendationRepository, NutrientComparator comparator) {
        this.foodItemRepository = foodItemRepository;
        this.recommendationRepository = recommendationRepository;
        this.comparator = comparator;
    }

    /** Recomputes all stored recommendations from the current food items. */
    @Transactional
    public int refreshAll() {
        recommendationRepository.deleteAllInBatch();
        List<FoodItem> items = foodItemRepository.findAllByOrderByNameAsc();
        List<Recommendation> recs = new ArrayList<>();
        for (FoodItem source : items) {
            items.stream()
                    .filter(alt -> !alt.getId().equals(source.getId()))
                    .map(alt -> comparator.compare(source, alt))
                    .filter(ComparisonDto::recommended)
                    .sorted(Comparator.comparingDouble(ComparisonDto::score).reversed()
                            .thenComparing(c -> c.alternative().name()))
                    .limit(MAX_ALTERNATIVES)
                    .forEach(c -> recs.add(new Recommendation(source, find(items, c.alternative().id()),
                            BigDecimal.valueOf(c.score()), c.summary())));
        }
        recommendationRepository.saveAll(recs);
        log.info("Recomputed {} recommendations for {} food items", recs.size(), items.size());
        return recs.size();
    }

    @Transactional(readOnly = true)
    public List<RecommendationGroupDto> list(Long foodItemId) {
        List<FoodItem> items = foodItemId == null ? foodItemRepository.findAllByOrderByNameAsc()
                : foodItemRepository.findByIdIn(List.of(foodItemId));
        if (foodItemId != null && items.isEmpty()) {
            throw new ResourceNotFoundException("Food item", foodItemId);
        }
        Map<Long, List<Recommendation>> bySource = (foodItemId == null
                ? recommendationRepository.findAllByOrderBySourceFoodItemNameAscImprovementScoreDesc()
                : recommendationRepository.findBySourceFoodItemIdOrderByImprovementScoreDesc(foodItemId))
                .stream().collect(Collectors.groupingBy(r -> r.getSourceFoodItem().getId()));
        Map<Long, FoodItem> allItems = foodItemRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(FoodItem::getId, Function.identity()));

        List<RecommendationGroupDto> groups = new ArrayList<>();
        for (FoodItem source : items) {
            boolean sufficient = FoodItemMapper.missingNutrients(source).stream()
                    .filter(n -> !n.equals("PROTEIN")).count() <= 4 - NutrientComparator.MIN_LIMIT_NUTRIENTS_KNOWN;
            List<RecommendationGroupDto.Alternative> alts = bySource.getOrDefault(source.getId(), List.of()).stream()
                    .map(r -> new RecommendationGroupDto.Alternative(r.getId(), r.getImprovementScore(),
                            r.getExplanation(),
                            comparator.compare(source, allItems.get(r.getAlternativeFoodItem().getId()))))
                    .toList();
            String message;
            if (!sufficient) {
                message = "Nutrition data for this item is insufficient; no reliable comparison can be made.";
            } else if (alts.isEmpty()) {
                message = "No available item is lower in at least two of energy, sugar, sodium and saturated fat "
                        + "without being higher in others.";
            } else {
                message = alts.size() + " alternative(s) found using available nutrition records.";
            }
            groups.add(new RecommendationGroupDto(FoodItemMapper.toDto(source), sufficient, message, alts));
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public ComparisonDto compare(Long sourceId, Long alternativeId) {
        if (sourceId.equals(alternativeId)) {
            throw new InvalidRequestException("Choose two different food items to compare");
        }
        List<FoodItem> items = foodItemRepository.findByIdIn(List.of(sourceId, alternativeId));
        return comparator.compare(find(items, sourceId), find(items, alternativeId));
    }

    private static FoodItem find(List<FoodItem> items, Long id) {
        return items.stream().filter(i -> i.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Food item", id));
    }
}
