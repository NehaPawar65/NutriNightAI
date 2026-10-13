package com.nutrinight.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Food categories shared with the AI service classifier. */
public enum FoodCategory {
    FAST_FOOD("Fast food"),
    FRIED_SNACKS("Fried snacks"),
    SUGARY_BEVERAGES("Sugary beverages"),
    DESSERTS("Desserts"),
    BALANCED_MEALS("Balanced meals"),
    FRUITS_VEGETABLES("Fruits and vegetables"),
    OTHER("Other / mixed dishes");

    /** Categories treated as "discretionary" (energy-dense, often high in sugar, salt or fat). */
    public static final Set<FoodCategory> DISCRETIONARY = Set.of(FAST_FOOD, FRIED_SNACKS, SUGARY_BEVERAGES, DESSERTS);

    private final String label;

    FoodCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Lenient parsing of user-provided category text: accepts enum names and labels such as
     * "Fast food", "fried-snacks" or "Fruits &amp; vegetables".
     */
    public static Optional<FoodCategory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT)
                .replace("&", "AND")
                .replaceAll("[^A-Z]+", "_")
                .replaceAll("^_|_$", "");
        key = key.replace("_AND_", "_");
        for (FoodCategory c : values()) {
            if (c.name().equals(key) || c.label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_").equals(key)) {
                return Optional.of(c);
            }
        }
        return switch (key) {
            case "FRUIT_VEGETABLES", "FRUITS_VEG", "FRUITS", "VEGETABLES", "FRUIT_VEG" -> Optional.of(FRUITS_VEGETABLES);
            case "BEVERAGES", "SUGARY_DRINKS", "SOFT_DRINKS" -> Optional.of(SUGARY_BEVERAGES);
            case "DESSERT", "SWEETS" -> Optional.of(DESSERTS);
            case "SNACKS", "FRIED_SNACK" -> Optional.of(FRIED_SNACKS);
            case "BALANCED_MEAL" -> Optional.of(BALANCED_MEALS);
            default -> Optional.empty();
        };
    }
}
