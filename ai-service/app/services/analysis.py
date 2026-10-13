"""Late-night consumption-pattern analysis with Pandas."""
from __future__ import annotations

import math

import pandas as pd

from app.services.indicator import compute_indicator
from ml.preprocessing import NUTRIENT_COLUMNS, LateNightWindow, records_to_frame

NUTRIENT_LABELS = {
    "calories_kcal": ("Energy", "kcal"),
    "sugar_g": ("Sugar", "g"),
    "sodium_mg": ("Sodium", "mg"),
    "saturated_fat_g": ("Saturated fat", "g"),
    "protein_g": ("Protein", "g"),
}
CATEGORY_LABELS = {
    "FAST_FOOD": "Fast food", "FRIED_SNACKS": "Fried snacks", "SUGARY_BEVERAGES": "Sugary beverages",
    "DESSERTS": "Desserts", "BALANCED_MEALS": "Balanced meals", "FRUITS_VEGETABLES": "Fruits and vegetables",
    "OTHER": "Other / mixed dishes",
}


def _r(value, digits: int = 2):
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    return round(float(value), digits)


def analyze(records: list[dict], window: LateNightWindow, weights: dict[str, float] | None = None) -> dict:
    df = records_to_frame(records, window)
    late = df[df["late_night"]]
    total, n_late = len(df), len(late)

    hourly_counts = df.groupby("hour").size().reindex(range(24), fill_value=0)
    hourly = [{"hour": h, "orders": int(hourly_counts[h]), "lateNight": bool(window.contains(pd.Timestamp(2000, 1, 1, h).time()))}
              for h in range(24)]
    peak_late = None
    if n_late:
        late_counts = late.groupby("hour").size()
        peak_late = int(late_counts.idxmax())

    categories = []
    if n_late:
        counts = late["category"].value_counts()
        for cat, n in counts.items():
            sub = late[late["category"] == cat]
            categories.append({
                "category": cat, "label": CATEGORY_LABELS.get(cat, cat), "orders": int(n),
                "share": _r(n / n_late, 4),
                "meanKcalPerOrder": _r(sub["calories_kcal_order"].mean(), 1),
            })

    nights = df.groupby("weekend")["analysis_date"].nunique()
    day_types = []
    for weekend, label in ((False, "WEEKDAY"), (True, "WEEKEND")):
        sub = df[df["weekend"] == weekend]
        sub_late = sub[sub["late_night"]]
        n_nights = int(nights.get(weekend, 0))
        day_types.append({
            "dayType": label,
            "nights": n_nights,
            "orders": len(sub),
            "lateNightOrders": len(sub_late),
            "lateNightOrdersPerNight": _r(len(sub_late) / n_nights, 2) if n_nights else None,
            "lateNightShare": _r(len(sub_late) / len(sub), 4) if len(sub) else None,
            "meanKcalPerLateNightOrder": _r(sub_late["calories_kcal_order"].mean(), 1),
            "discretionaryShareLateNight": _r(sub_late["discretionary"].mean(), 4) if len(sub_late) else None,
        })

    nutrition = []
    for col in NUTRIENT_COLUMNS:
        label, unit = NUTRIENT_LABELS[col]
        known = late[f"{col}_order"].dropna()
        nutrition.append({
            "nutrient": col, "label": label, "unit": unit,
            "meanPerLateNightOrder": _r(known.mean(), 2) if len(known) else None,
            "medianPerLateNightOrder": _r(known.median(), 2) if len(known) else None,
            "totalLateNight": _r(known.sum(), 1) if len(known) else None,
            "ordersWithData": int(len(known)),
            "ordersMissing": int(n_late - len(known)),
            "coverage": _r(len(known) / n_late, 4) if n_late else None,
        })

    recurrence = _recurrence(df, late)
    indicator = compute_indicator(df, weights)
    insights = _insights(total, n_late, peak_late, categories, day_types, indicator)

    return {
        "summary": {
            "totalOrders": total,
            "lateNightOrders": n_late,
            "lateNightShare": _r(n_late / total, 4) if total else None,
            "peakHour": int(hourly_counts.idxmax()),
            "peakLateNightHour": peak_late,
            "analysisDays": int(df["analysis_date"].nunique()),
            "periodStart": df["analysis_date"].min().date().isoformat(),
            "periodEnd": df["analysis_date"].max().date().isoformat(),
            "lateNightWindow": {"start": window.start.isoformat("minutes"), "end": window.end.isoformat("minutes"),
                                "startInclusive": True, "endExclusive": True},
        },
        "hourly": hourly,
        "lateNightCategories": categories,
        "dayTypes": day_types,
        "lateNightNutrition": nutrition,
        "recurrence": recurrence,
        "indicator": indicator,
        "insights": insights,
        "methodology": [
            "Late-night = local order time 22:00 (inclusive) to 04:00 (exclusive); after-midnight orders are "
            "attributed to the previous evening's analysis date.",
            "Weekend = analysis dates falling on Saturday or Sunday (i.e. Saturday and Sunday nights).",
            "Per-order nutrients = per-serving value x quantity; means use only orders with known values.",
            "Recurrence is population-level (share of nights on which a category was ordered late at night); "
            "no individual consumer is tracked.",
        ],
    }


def _recurrence(df: pd.DataFrame, late: pd.DataFrame) -> dict:
    """How regularly each category appears in late-night orders across nights (no individual tracking)."""
    nights_total = int(df["analysis_date"].nunique())
    if late.empty or nights_total == 0:
        return {"available": False, "reason": "no late-night orders", "categories": []}
    per_cat = late.groupby("category")["analysis_date"].nunique().sort_values(ascending=False)
    return {
        "available": True,
        "nightsInPeriod": nights_total,
        "note": "Repeated consumption by the same person cannot be assessed: the data holds no consumer "
                "identifier (by design). Recurrence is measured across nights for the population instead.",
        "categories": [
            {"category": cat, "label": CATEGORY_LABELS.get(cat, cat), "nightsWithOrders": int(n),
             "shareOfNights": _r(n / nights_total, 4)}
            for cat, n in per_cat.items()
        ],
    }


def _insights(total, n_late, peak_late, categories, day_types, indicator) -> list[str]:
    out = []
    if total:
        out.append(f"{n_late:,} of {total:,} orders ({n_late / total:.1%}) were placed in the late-night window.")
    if peak_late is not None:
        out.append(f"Late-night ordering peaked in the {peak_late:02d}:00 hour.")
    if categories:
        top = categories[0]
        out.append(f"{top['label']} was the most frequent late-night category ({top['share']:.1%} of late-night orders).")
    wd, we = day_types
    if wd["lateNightOrdersPerNight"] and we["lateNightOrdersPerNight"]:
        ratio = we["lateNightOrdersPerNight"] / wd["lateNightOrdersPerNight"]
        out.append(f"Weekend nights averaged {we['lateNightOrdersPerNight']} late-night orders vs "
                   f"{wd['lateNightOrdersPerNight']} on weekday nights (ratio {ratio:.2f}); this is an observed "
                   f"difference, not a tested causal effect.")
    if indicator.get("score") is not None:
        top_c = max((c for c in indicator["components"] if c["available"]),
                    key=lambda c: c["contributionPoints"] or 0, default=None)
        msg = f"The Late-Night Dietary Pattern Indicator is {indicator['score']} / 100 ({indicator['band'].lower()} band)"
        if top_c:
            msg += f"; the largest contributor is '{top_c['label']}' ({top_c['contributionPoints']} points)"
        out.append(msg + ".")
    return out
