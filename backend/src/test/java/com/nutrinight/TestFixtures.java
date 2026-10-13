package com.nutrinight;

import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.domain.CategorySource;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.entity.FoodItem;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static NutriNightProperties properties() {
        return new NutriNightProperties("Asia/Kolkata",
                new NutriNightProperties.LateNight(LocalTime.of(22, 0), LocalTime.of(4, 0)),
                new NutriNightProperties.Privacy(10),
                new NutriNightProperties.Upload(10 * 1024 * 1024, 1000, 50, 5),
                new NutriNightProperties.Ai("http://localhost:1", "key", Duration.ofSeconds(1), Duration.ofSeconds(2), 1000),
                new NutriNightProperties.Security("admin", "secret"),
                new NutriNightProperties.Cors(List.of("http://localhost:5180")),
                new NutriNightProperties.NutritionThresholds("test", null,
                        new NutriNightProperties.ThresholdSet(new NutriNightProperties.Range(5, 22.5),
                                new NutriNightProperties.Range(1.5, 5), new NutriNightProperties.Range(120, 600)),
                        new NutriNightProperties.ThresholdSet(new NutriNightProperties.Range(2.5, 11.25),
                                new NutriNightProperties.Range(0.75, 2.5), new NutriNightProperties.Range(120, 300))));
    }

    /** Nutrients: kcal, sugar, sodium, sat fat, protein (nullable = missing). */
    public static FoodItem item(long id, String name, FoodCategory category, double serving, String unit,
                                Double kcal, Double sugar, Double sodium, Double satFat, Double protein) {
        FoodItem item = new FoodItem(name, category, CategorySource.PROVIDED);
        item.setId(id);
        item.setServing(BigDecimal.valueOf(serving), unit);
        item.setNutrients(bd(kcal), bd(sugar), bd(sodium), bd(satFat), bd(protein));
        return item;
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
