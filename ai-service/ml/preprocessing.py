"""Data preprocessing shared by analysis, clustering and the classifier.

The late-night window is evaluated on local wall-clock time with the start inclusive and the
end exclusive (22:00 <= t < 04:00 by default). Orders after midnight belong to the previous
evening's "analysis date", so a night is never split across two calendar days.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from typing import Iterable

import numpy as np
import pandas as pd

NUTRIENT_COLUMNS = ["calories_kcal", "sugar_g", "sodium_mg", "saturated_fat_g", "protein_g"]
DISCRETIONARY_CATEGORIES = {"FAST_FOOD", "FRIED_SNACKS", "SUGARY_BEVERAGES", "DESSERTS"}
CATEGORIES = [
    "FAST_FOOD", "FRIED_SNACKS", "SUGARY_BEVERAGES", "DESSERTS",
    "BALANCED_MEALS", "FRUITS_VEGETABLES", "OTHER",
]


@dataclass(frozen=True)
class LateNightWindow:
    start: time = time(22, 0)
    end: time = time(4, 0)

    def __post_init__(self) -> None:
        if self.start == self.end:
            raise ValueError("late-night window start and end must differ")

    @property
    def wraps_midnight(self) -> bool:
        return self.end < self.start

    def contains(self, t: time) -> bool:
        if self.wraps_midnight:
            return t >= self.start or t < self.end
        return self.start <= t < self.end

    def analysis_date(self, dt: datetime) -> date:
        if self.wraps_midnight and dt.time() < self.end:
            return (dt - timedelta(days=1)).date()
        return dt.date()

    @staticmethod
    def parse(start: str, end: str) -> "LateNightWindow":
        return LateNightWindow(_parse_hhmm(start), _parse_hhmm(end))


def _parse_hhmm(text: str) -> time:
    try:
        return time.fromisoformat(text)
    except ValueError as exc:
        raise ValueError(f"invalid time '{text}', expected HH:MM") from exc


def late_night_mask(local_times: pd.Series, window: LateNightWindow) -> pd.Series:
    """Vectorised window test on a datetime64 Series of local times."""
    minutes = local_times.dt.hour * 60 + local_times.dt.minute + local_times.dt.second / 60.0
    start = window.start.hour * 60 + window.start.minute + window.start.second / 60.0
    end = window.end.hour * 60 + window.end.minute + window.end.second / 60.0
    if window.wraps_midnight:
        return (minutes >= start) | (minutes < end)
    return (minutes >= start) & (minutes < end)


def analysis_dates(local_times: pd.Series, window: LateNightWindow) -> pd.Series:
    """Analysis date per order: early-morning times inside a wrapping window move to the previous day."""
    dates = local_times.dt.normalize()
    if window.wraps_midnight:
        end = window.end.hour * 60 + window.end.minute + window.end.second / 60.0
        minutes = local_times.dt.hour * 60 + local_times.dt.minute + local_times.dt.second / 60.0
        dates = dates.where(minutes >= end, dates - pd.Timedelta(days=1))
    return dates


def normalize_text(text: str) -> str:
    """Lower-case, strip accents and punctuation, collapse whitespace."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-z0-9\s]", " ", text.lower())
    return re.sub(r"\s+", " ", text).strip()


def records_to_frame(records: Iterable[dict], window: LateNightWindow) -> pd.DataFrame:
    """Build the analysis frame from order records (per-serving nutrients + quantity).

    Adds: hour, late_night (if not supplied), analysis_date, weekend (by analysis date),
    per-order nutrient totals (<nutrient>_order = per-serving * quantity; NaN when missing).
    Missing nutrient values stay NaN and are never treated as zero.
    """
    df = pd.DataFrame(list(records))
    if df.empty:
        raise ValueError("no records supplied")
    df["local_date_time"] = pd.to_datetime(df["local_date_time"])
    df["hour"] = df["local_date_time"].dt.hour
    computed = late_night_mask(df["local_date_time"], window)
    if "late_night" in df.columns:
        df["late_night"] = df["late_night"].where(df["late_night"].notna(), computed).astype(bool)
    else:
        df["late_night"] = computed
    if "analysis_date" in df.columns and df["analysis_date"].notna().all():
        df["analysis_date"] = pd.to_datetime(df["analysis_date"])
    else:
        df["analysis_date"] = analysis_dates(df["local_date_time"], window)
    df["weekend"] = df["analysis_date"].dt.dayofweek >= 5
    df["quantity"] = df.get("quantity", 1)
    for col in NUTRIENT_COLUMNS:
        if col not in df.columns:
            df[col] = np.nan
        df[col] = pd.to_numeric(df[col], errors="coerce")
        df[f"{col}_order"] = df[col] * df["quantity"]
    if "city" not in df.columns:
        df["city"] = "Unknown"
    df["discretionary"] = df["category"].isin(DISCRETIONARY_CATEGORIES)
    return df
