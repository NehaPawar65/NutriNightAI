package com.nutrinight.domain;

/** How a food item's category was obtained. */
public enum CategorySource {
    /** Supplied in the imported data. */
    PROVIDED,
    /** Assigned by the AI service's keyword rules. */
    AI_RULE_BASED,
    /** Assigned by the AI service's trained TF-IDF + logistic regression model. */
    AI_ML_MODEL,
    /** No category supplied and the AI service was unavailable; defaulted to OTHER. */
    DEFAULT
}
