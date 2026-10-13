package com.nutrinight.service;

import com.nutrinight.TestFixtures;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.Nutrient;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.service.NutritionCalculator.Level;
import com.nutrinight.service.NutritionCalculator.Line;
import com.nutrinight.service.NutritionCalculator.NutrientStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionCalculatorTest {

    private final FoodItem pizza = TestFixtures.item(1, "Pizza", FoodCategory.FAST_FOOD, 200, "g", 500.0, 6.0, 1000.0, 10.0, 20.0);
    private final FoodItem salad = TestFixtures.item(2, "Salad", FoodCategory.FRUITS_VEGETABLES, 200, "g", 100.0, 0.0, 100.0, 0.5, 3.0);
    private final FoodItem momos = TestFixtures.item(3, "Momos", FoodCategory.OTHER, 200, "g", 340.0, null, 690.0, null, 18.0);

    @Test
    void totalsUseQuantityAndMeansUseOrderLines() {
        // 2 pizza order lines with 3 servings in total; 1 salad order line with 1 serving
        NutrientStat kcal = NutritionCalculator.summarize(List.of(new Line(pizza, 2, 3), new Line(salad, 1, 1)), Nutrient.CALORIES);
        assertThat(kcal.total()).isEqualByComparingTo("1600");            // 500*3 + 100*1
        assertThat(kcal.meanPerOrder()).isEqualByComparingTo("533.33");    // 1600 / 3 order lines
        assertThat(kcal.meanPerServing()).isEqualByComparingTo("366.67");  // (500*2 + 100*1) / 3
        assertThat(kcal.ordersWithData()).isEqualTo(3);
        assertThat(kcal.ordersMissing()).isZero();
        assertThat(kcal.coveragePercent()).isEqualTo(100.0);
    }

    @Test
    void missingValuesAreExcludedButGenuineZerosCount() {
        List<Line> lines = List.of(new Line(salad, 1, 1), new Line(momos, 3, 3));
        NutrientStat sugar = NutritionCalculator.summarize(lines, Nutrient.SUGAR);
        assertThat(sugar.ordersWithData()).isEqualTo(1);      // the salad's genuine 0 g counts
        assertThat(sugar.ordersMissing()).isEqualTo(3);       // momos have no sugar value
        assertThat(sugar.meanPerOrder()).isEqualByComparingTo("0");
        assertThat(sugar.coveragePercent()).isEqualTo(25.0);

        NutrientStat onlyMissing = NutritionCalculator.summarize(List.of(new Line(momos, 2, 2)), Nutrient.SUGAR);
        assertThat(onlyMissing.meanPerOrder()).isNull();
        assertThat(onlyMissing.total()).isNull();
        assertThat(onlyMissing.coveragePercent()).isEqualTo(0.0);
    }

    @Test
    void emptyInputHasNoCoverage() {
        NutrientStat stat = NutritionCalculator.summarize(List.of(), Nutrient.SODIUM);
        assertThat(stat.meanPerOrder()).isNull();
        assertThat(stat.coveragePercent()).isNull();
    }

    @Test
    void levelsUsePer100gFoodAndPer100mlDrinkThresholds() {
        var t = TestFixtures.properties().nutritionThresholds();
        // pizza sodium 1000 mg / 200 g = 500 mg/100 g -> MEDIUM (120 < x <= 600)
        assertThat(NutritionCalculator.level(pizza, Nutrient.SODIUM, t)).isEqualTo(Level.MEDIUM);
        // pizza sat fat 10 g / 200 g = 5 g/100 g -> MEDIUM (exactly at the high cut-off is not "above")
        assertThat(NutritionCalculator.level(pizza, Nutrient.SATURATED_FAT, t)).isEqualTo(Level.MEDIUM);
        assertThat(NutritionCalculator.level(salad, Nutrient.SUGAR, t)).isEqualTo(Level.LOW);
        assertThat(NutritionCalculator.level(momos, Nutrient.SUGAR, t)).isEqualTo(Level.UNKNOWN);

        FoodItem cola = TestFixtures.item(4, "Cola", FoodCategory.SUGARY_BEVERAGES, 500, "ml", 210.0, 53.0, 20.0, 0.0, 0.0);
        // cola: 10.6 g sugar/100 ml -> MEDIUM under drink thresholds (high is > 11.25 g/100 ml)
        FoodItem shake = TestFixtures.item(5, "Shake", FoodCategory.SUGARY_BEVERAGES, 300, "ml", 300.0, 36.0, 150.0, 6.0, 8.0);
        assertThat(NutritionCalculator.level(cola, Nutrient.SUGAR, t)).isEqualTo(Level.MEDIUM);
        // 12 g/100 ml -> HIGH under drink thresholds (it would be MEDIUM under food thresholds)
        assertThat(NutritionCalculator.level(shake, Nutrient.SUGAR, t)).isEqualTo(Level.HIGH);
        assertThat(NutritionCalculator.level(shake, Nutrient.SATURATED_FAT, t)).isEqualTo(Level.MEDIUM);
    }
}
