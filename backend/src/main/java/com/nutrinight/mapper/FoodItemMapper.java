package com.nutrinight.mapper;

import com.nutrinight.domain.Nutrient;
import com.nutrinight.dto.FoodItemDto;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.entity.NutritionSource;

import java.util.Arrays;
import java.util.List;

public final class FoodItemMapper {

    private FoodItemMapper() {
    }

    public static FoodItemDto toDto(FoodItem item) {
        NutritionSource source = item.getNutritionSource();
        return new FoodItemDto(item.getId(), item.getName(), item.getCategory().name(), item.getCategory().getLabel(),
                item.getCategorySource().name(), item.getServingSize(), item.getServingUnit(), item.getCaloriesKcal(),
                item.getSugarG(), item.getSodiumMg(), item.getSaturatedFatG(), item.getProteinG(),
                source == null ? null : source.getSourceName(), source == null ? null : source.getSourceUrl(),
                missingNutrients(item));
    }

    public static List<String> missingNutrients(FoodItem item) {
        return Arrays.stream(Nutrient.values()).filter(n -> n.perServing(item) == null).map(Enum::name).toList();
    }
}
