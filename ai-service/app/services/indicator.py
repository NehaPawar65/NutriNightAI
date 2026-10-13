"""Late-Night Dietary Pattern Indicator (LNDPI), a configurable 0-100 population-level indicator.

Formula
-------
For each available component i with raw value x_i and reference value r_i:

    normalised_i = min(x_i / r_i, 1)                      (0 = none, 1 = at or above the reference)
    LNDPI        = 100 * sum(w_i * normalised_i) / sum(w_i)  over AVAILABLE components only

Higher values mean the late-night orders in the selected period are, on average, more energy-dense,
higher in sugar / sodium / saturated fat, and more concentrated in discretionary categories.

Reference values are anchored to widely cited daily guidance (WHO: <2000 mg sodium/day and free
sugars <10% of energy (~50 g/day on 2000 kcal); EU/UK reference intakes: 2000 kcal and 20 g saturated
fat/day). A single late-night order reaching ~40-50% of a daily reference is used as the saturation
point. These anchors and the weights are a transparent ASSUMPTION for exploratory research: they are
not clinically validated thresholds, and the indicator is NOT an individual probability of developing
an NCD.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import pandas as pd

INDICATOR_VERSION = "lndpi-1.0"

DEFAULT_WEIGHTS: dict[str, float] = {
    "late_night_share": 0.15,
    "energy_per_order": 0.20,
    "sugar_per_order": 0.15,
    "sodium_per_order": 0.15,
    "saturated_fat_per_order": 0.15,
    "discretionary_share": 0.20,
}

# Minimum share of late-night orders with a known value for a nutrient component to be used.
MIN_COVERAGE = 0.5


@dataclass(frozen=True)
class Component:
    key: str
    label: str
    unit: str
    reference: float
    description: str
    column: str | None = None  # per-order nutrient column, when nutrient-based


COMPONENTS = [
    Component("late_night_share", "Share of orders placed 22:00-04:00", "fraction", 0.5,
              "Behavioural: fraction of all orders in the period that fall in the late-night window. "
              "Saturates when half of all orders are late-night."),
    Component("energy_per_order", "Mean energy per late-night order", "kcal", 800.0,
              "Mean kcal per late-night order (per-serving kcal x quantity). Reference 800 kcal = 40% of a "
              "2000 kcal reference intake.", "calories_kcal_order"),
    Component("sugar_per_order", "Mean sugar per late-night order", "g", 25.0,
              "Mean total sugars per late-night order. Reference 25 g = half of the WHO free-sugar "
              "upper limit for a 2000 kcal diet (total sugars are used as a proxy).", "sugar_g_order"),
    Component("sodium_per_order", "Mean sodium per late-night order", "mg", 1000.0,
              "Mean sodium per late-night order. Reference 1000 mg = half of the WHO daily maximum of 2000 mg.",
              "sodium_mg_order"),
    Component("saturated_fat_per_order", "Mean saturated fat per late-night order", "g", 10.0,
              "Mean saturated fat per late-night order. Reference 10 g = half of the 20 g daily reference intake.",
              "saturated_fat_g_order"),
    Component("discretionary_share", "Share of late-night orders in discretionary categories", "fraction", 1.0,
              "Fraction of late-night orders that are fast food, fried snacks, sugary beverages or desserts."),
]


def validate_weights(overrides: dict[str, float] | None) -> dict[str, float]:
    weights = dict(DEFAULT_WEIGHTS)
    if not overrides:
        return weights
    unknown = set(overrides) - set(DEFAULT_WEIGHTS)
    if unknown:
        raise ValueError(f"unknown indicator weight(s): {sorted(unknown)}; allowed: {sorted(DEFAULT_WEIGHTS)}")
    for key, value in overrides.items():
        if value is None or not math.isfinite(value) or value < 0 or value > 10:
            raise ValueError(f"weight '{key}' must be a finite number between 0 and 10")
        weights[key] = float(value)
    if sum(weights.values()) <= 0:
        raise ValueError("at least one indicator weight must be positive")
    return weights


def band(score: float) -> str:
    if score < 33.3:
        return "LOWER"
    if score < 66.7:
        return "MODERATE"
    return "HIGHER"


def compute_indicator(df: pd.DataFrame, weights: dict[str, float] | None = None) -> dict:
    """Compute the LNDPI from an analysis frame produced by ``records_to_frame``."""
    weights = validate_weights(weights)
    late = df[df["late_night"]]
    total = len(df)
    n_late = len(late)

    components = []
    for c in COMPONENTS:
        raw: float | None = None
        coverage = 1.0
        reason = None
        if c.key == "late_night_share":
            raw = n_late / total if total else None
        elif n_late == 0:
            reason = "no late-night orders in the selected data"
        elif c.key == "discretionary_share":
            raw = float(late["discretionary"].mean())
        else:
            known = late[c.column].notna()
            coverage = float(known.mean())
            if coverage < MIN_COVERAGE:
                reason = f"nutrient known for only {coverage:.0%} of late-night orders (minimum {MIN_COVERAGE:.0%})"
            else:
                raw = float(late.loc[known, c.column].mean())
        available = raw is not None and reason is None
        normalised = min(raw / c.reference, 1.0) if available else None
        components.append({
            "key": c.key, "label": c.label, "unit": c.unit, "description": c.description,
            "rawValue": round(raw, 3) if raw is not None else None,
            "reference": c.reference, "normalized": round(normalised, 4) if normalised is not None else None,
            "weight": weights[c.key], "dataCoverage": round(coverage, 4), "available": available,
            "unavailableReason": reason,
        })

    used = [c for c in components if c["available"] and c["weight"] > 0]
    weight_sum = sum(c["weight"] for c in used)
    score = None
    for c in components:
        c["contributionPoints"] = None
    if weight_sum > 0:
        score = 0.0
        for c in used:
            points = 100.0 * c["weight"] * c["normalized"] / weight_sum
            c["contributionPoints"] = round(points, 2)
            score += points
        score = round(score, 1)

    total_weight = sum(weights.values())
    return {
        "name": "Late-Night Dietary Pattern Indicator",
        "version": INDICATOR_VERSION,
        "score": score,
        "band": band(score) if score is not None else None,
        "bandNote": "Bands (LOWER <33.3, MODERATE <66.7, HIGHER) are descriptive cut-offs of this indicator, "
                    "not clinical risk categories.",
        "components": components,
        "weightCoverage": round(weight_sum / total_weight, 4) if total_weight else 0.0,
        "lateNightOrders": n_late,
        "totalOrders": total,
        "formula": "LNDPI = 100 * sum(w_i * min(x_i / r_i, 1)) / sum(w_i), over available components",
        "missingDataPolicy": f"Nutrient components are computed over orders with known values and are excluded "
                             f"(weights renormalised) when fewer than {MIN_COVERAGE:.0%} of late-night orders "
                             f"have the nutrient. Missing values are never treated as zero.",
        "disclaimer": "Population-level descriptive indicator built on configurable, non-validated weights. "
                      "It does not diagnose disease and is not an individual's probability of developing "
                      "obesity, diabetes or cardiovascular disease.",
    }
