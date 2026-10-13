"""Exploratory K-Means clustering of city-night consumption profiles.

Each unit is one city on one analysis night. Features describe what was ordered (late-night
volume, discretionary share and mean nutrients per order). Clusters are descriptive groupings of
similar nights; they are not diagnoses or clinical risk categories.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score
from sklearn.preprocessing import StandardScaler

from ml.preprocessing import LateNightWindow, records_to_frame

FEATURES = {
    "orders": ("Late-night orders", "orders"),
    "discretionary_share": ("Discretionary share", "fraction"),
    "mean_kcal": ("Mean energy per order", "kcal"),
    "mean_sugar": ("Mean sugar per order", "g"),
    "mean_sodium": ("Mean sodium per order", "mg"),
    "mean_sat_fat": ("Mean saturated fat per order", "g"),
}


def build_units(df: pd.DataFrame, min_orders: int) -> tuple[pd.DataFrame, int]:
    grouped = df.groupby(["city", "analysis_date"])
    units = pd.DataFrame({
        "orders": grouped.size(),
        "discretionary_share": grouped["discretionary"].mean(),
        "mean_kcal": grouped["calories_kcal_order"].mean(),
        "mean_sugar": grouped["sugar_g_order"].mean(),
        "mean_sodium": grouped["sodium_mg_order"].mean(),
        "mean_sat_fat": grouped["saturated_fat_g_order"].mean(),
    })
    before = len(units)
    units = units[units["orders"] >= min_orders].dropna()
    return units, before - len(units)


def quality_label(silhouette: float) -> str:
    if silhouette >= 0.5:
        return "STRONG"
    if silhouette >= 0.25:
        return "REASONABLE"
    return "WEAK"


def cluster(records: list[dict], window: LateNightWindow, late_night_only: bool = True, max_clusters: int = 5,
            min_orders_per_unit: int = 3, random_state: int = 42) -> dict:
    df = records_to_frame(records, window)
    if late_night_only:
        df = df[df["late_night"]]
    units, excluded = build_units(df, min_orders_per_unit) if not df.empty else (pd.DataFrame(), 0)
    base = {
        "unit": "city x analysis night",
        "lateNightOnly": late_night_only,
        "features": [{"key": k, "label": v[0], "unit": v[1]} for k, v in FEATURES.items()],
        "unitsExcluded": excluded,
        "minOrdersPerUnit": min_orders_per_unit,
        "disclaimer": "Exploratory grouping of similar city-nights. Clusters are not diagnoses or clinical "
                      "risk categories, and K-Means always returns clusters even when no real structure exists; "
                      "check the silhouette score before interpreting them.",
    }
    if len(units) < 10:
        return {**base, "available": False, "unitsAnalyzed": int(len(units)),
                "reason": f"at least 10 city-nights with >= {min_orders_per_unit} orders are needed "
                          f"(found {len(units)})", "clusters": [], "candidates": []}

    x = StandardScaler().fit_transform(units.values)
    candidates = []
    best = None
    for k in range(2, min(max_clusters, len(units) - 1) + 1):
        model = KMeans(n_clusters=k, n_init=10, random_state=random_state).fit(x)
        score = float(silhouette_score(x, model.labels_, random_state=random_state))
        candidates.append({"k": k, "silhouette": round(score, 4), "inertia": round(float(model.inertia_), 2)})
        if best is None or score > best[1]:
            best = (model, score)
    model, score = best
    labels = model.labels_
    overall_mean = units.mean()
    overall_std = units.std(ddof=0).replace(0, np.nan)

    clusters = []
    for cid in range(model.n_clusters):
        members = units[labels == cid]
        centroid = members.mean()
        z = ((centroid - overall_mean) / overall_std).fillna(0)
        clusters.append({
            "id": cid,
            "size": int(len(members)),
            "share": round(len(members) / len(units), 4),
            "centroid": {k: round(float(centroid[k]), 3) for k in FEATURES},
            "zScores": {k: round(float(z[k]), 3) for k in FEATURES},
            "description": describe(z),
            "topCities": members.reset_index()["city"].value_counts().head(3).index.tolist(),
        })
    clusters.sort(key=lambda c: -c["size"])
    q = quality_label(score)
    return {
        **base,
        "available": True,
        "algorithm": "K-Means (k-means++, n_init=10) on standardised features; k chosen by best silhouette",
        "unitsAnalyzed": int(len(units)),
        "k": int(model.n_clusters),
        "silhouette": round(score, 4),
        "quality": q,
        "qualityNote": {
            "STRONG": "Clusters are well separated.",
            "REASONABLE": "Clusters show some structure; interpret with care.",
            "WEAK": "Little cluster structure: the groups are not meaningfully distinct and should not be "
                    "interpreted as real segments.",
        }[q],
        "candidates": candidates,
        "clusters": clusters,
    }


def describe(z: pd.Series) -> str:
    """Plain-language description from centroid z-scores (|z| >= 0.5 counts as notably higher/lower)."""
    parts = []
    for key, (label, _) in FEATURES.items():
        if z[key] >= 0.5:
            parts.append(f"higher {label.lower()}")
        elif z[key] <= -0.5:
            parts.append(f"lower {label.lower()}")
    return ("Nights with " + ", ".join(parts)) if parts else "Nights close to the overall average"
