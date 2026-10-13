package com.nutrinight.service;

import com.nutrinight.TestFixtures;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.dto.ComparisonDto;
import com.nutrinight.entity.FoodItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NutrientComparatorTest {

    private final NutrientComparator comparator = new NutrientComparator();
    private final FoodItem pizza = TestFixtures.item(1, "Pizza", FoodCategory.FAST_FOOD, 200, "g", 530.0, 6.0, 1100.0, 10.0, 22.0);
    private final FoodItem khichdi = TestFixtures.item(2, "Khichdi", FoodCategory.BALANCED_MEALS, 350, "g", 380.0, 4.0, 650.0, 2.5, 14.0);

    @Test
    void recommendsWhenSeveralLimitNutrientsAreLower() {
        ComparisonDto c = comparator.compare(pizza, khichdi);
        assertThat(c.sufficientData()).isTrue();
        assertThat(c.recommended()).isTrue();
        assertThat(c.favourableCount()).isEqualTo(4);
        assertThat(c.unfavourableCount()).isZero();
        assertThat(c.summary()).contains("lower energy").contains("350 g vs 200 g");
        assertThat(c.nutrients()).filteredOn(n -> n.nutrient().equals("PROTEIN"))
                .singleElement().satisfies(n -> assertThat(n.assessment()).isEqualTo("UNFAVOURABLE"));
        assertThat(c.per100Comparable()).isTrue();
    }

    @Test
    void singleBetterNutrientIsNotEnough() {
        FoodItem lowSugarOnly = TestFixtures.item(3, "Burger", FoodCategory.FAST_FOOD, 180, "g", 600.0, 1.0, 1200.0, 11.0, 20.0);
        ComparisonDto c = comparator.compare(pizza, lowSugarOnly);
        assertThat(c.favourableCount()).isEqualTo(1);
        assertThat(c.recommended()).isFalse();
        assertThat(c.summary()).contains("not recommended");
    }

    @Test
    void differencesWithinTenPercentAreSimilar() {
        FoodItem similar = TestFixtures.item(3, "Pizza 2", FoodCategory.FAST_FOOD, 200, "g", 560.0, 6.3, 1050.0, 9.5, 22.0);
        ComparisonDto c = comparator.compare(pizza, similar);
        assertThat(c.nutrients()).allSatisfy(n -> assertThat(n.direction()).isEqualTo("SIMILAR"));
        assertThat(c.recommended()).isFalse();
    }

    @Test
    void insufficientDataProducesNoComparison() {
        FoodItem sparse = TestFixtures.item(3, "Momos", FoodCategory.OTHER, 200, "g", 340.0, null, null, null, 18.0);
        ComparisonDto c = comparator.compare(pizza, sparse);
        assertThat(c.sufficientData()).isFalse();
        assertThat(c.recommended()).isFalse();
        assertThat(c.summary()).startsWith("No reliable comparison can be made");
        assertThat(c.nutrients()).filteredOn(n -> n.direction().equals("UNAVAILABLE")).hasSize(3);
    }

    @Test
    void incompatibleRolesAreNotRecommended() {
        FoodItem water = TestFixtures.item(3, "Water", FoodCategory.OTHER, 500, "ml", 0.0, 0.0, 5.0, 0.0, 0.0);
        ComparisonDto c = comparator.compare(pizza, water);
        assertThat(c.favourableCount()).isGreaterThanOrEqualTo(2);
        assertThat(c.recommended()).isFalse(); // a drink does not replace a meal
        assertThat(c.per100Comparable()).isFalse(); // g vs ml
    }

    @Test
    void fruitCanReplaceDessert() {
        FoodItem dessert = TestFixtures.item(4, "Gulab Jamun", FoodCategory.DESSERTS, 100, "g", 380.0, 50.0, 40.0, 6.0, 5.0);
        FoodItem fruit = TestFixtures.item(5, "Fruit Bowl", FoodCategory.FRUITS_VEGETABLES, 250, "g", 140.0, 25.0, 10.0, 0.2, 2.0);
        assertThat(comparator.compare(dessert, fruit).recommended()).isTrue();
    }

    @Test
    void flagsAdvantagesThatOnlyComeFromSmallerServings() {
        FoodItem cola = TestFixtures.item(6, "Cola", FoodCategory.SUGARY_BEVERAGES, 500, "ml", 210.0, 53.0, 20.0, 0.0, 0.0);
        FoodItem juice = TestFixtures.item(7, "Juice", FoodCategory.SUGARY_BEVERAGES, 300, "ml", 150.0, 33.0, 15.0, 0.0, 1.0);
        ComparisonDto c = comparator.compare(cola, juice);
        assertThat(c.summary()).contains("per 100 ml").contains("sugar").contains("smaller serving size");
    }
}
