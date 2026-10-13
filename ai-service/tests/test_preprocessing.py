from datetime import datetime, time

import numpy as np
import pandas as pd
import pytest

from ml.preprocessing import LateNightWindow, analysis_dates, late_night_mask, normalize_text, records_to_frame
from tests.conftest import make_record

WINDOW = LateNightWindow()


@pytest.mark.parametrize("t,expected", [
    ("21:59:59", False), ("21:59", False), ("22:00", True), ("23:00", True), ("00:00", True),
    ("03:59", True), ("03:59:59", True), ("04:00", False), ("12:00", False),
])
def test_late_night_boundaries_scalar(t, expected):
    assert WINDOW.contains(time.fromisoformat(t)) is expected


def test_late_night_boundaries_vectorised_match_scalar():
    stamps = ["2026-07-01 21:59:59", "2026-07-01 22:00:00", "2026-07-01 23:00:00", "2026-07-02 00:00:00",
              "2026-07-02 03:59:59", "2026-07-02 04:00:00"]
    series = pd.to_datetime(pd.Series(stamps))
    assert late_night_mask(series, WINDOW).tolist() == [False, True, True, True, True, False]


def test_after_midnight_orders_belong_to_previous_evening():
    series = pd.to_datetime(pd.Series(["2026-07-04 01:30", "2026-07-03 23:30", "2026-07-04 04:00",
                                       "2027-01-01 00:15"]))
    result = analysis_dates(series, WINDOW).dt.date.astype(str).tolist()
    assert result == ["2026-07-03", "2026-07-03", "2026-07-04", "2026-12-31"]
    assert WINDOW.analysis_date(datetime(2026, 7, 4, 3, 59)).isoformat() == "2026-07-03"


def test_non_wrapping_window():
    w = LateNightWindow.parse("18:00", "21:00")
    assert w.contains(time(18, 0)) and not w.contains(time(21, 0))
    assert w.analysis_date(datetime(2026, 7, 4, 1, 0)).isoformat() == "2026-07-04"


def test_invalid_window():
    with pytest.raises(ValueError):
        LateNightWindow.parse("22:00", "22:00")
    with pytest.raises(ValueError):
        LateNightWindow.parse("25:00", "04:00")


def test_normalize_text():
    assert normalize_text("  Crème BRÛLÉE (2 pcs)!! ") == "creme brulee 2 pcs"


def test_records_to_frame_keeps_missing_nutrients_as_nan_and_multiplies_quantity():
    df = records_to_frame([
        make_record("2026-07-04T23:00:00", quantity=2, sugar_g=None),
        make_record("2026-07-04T23:30:00", quantity=1, sugar_g=0.0),
    ], WINDOW)
    assert np.isnan(df.loc[0, "sugar_g_order"])
    assert df.loc[1, "sugar_g_order"] == 0.0
    assert df.loc[0, "calories_kcal_order"] == 1000.0
    assert df["late_night"].all()
    # 4 July 2026 is a Saturday -> weekend night
    assert df["weekend"].all()


def test_supplied_late_night_flag_is_respected():
    rec = make_record("2026-07-04T12:00:00")
    rec["late_night"] = True
    assert records_to_frame([rec], WINDOW)["late_night"].iloc[0]


def test_empty_records_rejected():
    with pytest.raises(ValueError):
        records_to_frame([], WINDOW)
