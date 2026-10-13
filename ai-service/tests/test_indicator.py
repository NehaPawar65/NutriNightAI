import pytest

from app.services.indicator import DEFAULT_WEIGHTS, band, compute_indicator, validate_weights
from ml.preprocessing import LateNightWindow, records_to_frame
from tests.conftest import make_record

WINDOW = LateNightWindow()


def frame(records):
    return records_to_frame(records, WINDOW)


def test_score_matches_documented_formula():
    # 2 orders, 1 late-night: FAST_FOOD, 500 kcal, 6 g sugar, 1000 mg sodium, 9 g sat fat (quantity 1)
    df = frame([make_record("2026-07-01T23:00:00"), make_record("2026-07-01T13:00:00")])
    result = compute_indicator(df)
    expected_normalised = {
        "late_night_share": 0.5 / 0.5,
        "energy_per_order": 500 / 800,
        "sugar_per_order": 6 / 25,
        "sodium_per_order": 1000 / 1000,
        "saturated_fat_per_order": 9 / 10,
        "discretionary_share": 1.0,
    }
    expected = 100 * sum(DEFAULT_WEIGHTS[k] * v for k, v in expected_normalised.items()) / sum(DEFAULT_WEIGHTS.values())
    assert result["score"] == pytest.approx(round(expected, 1))
    contributions = sum(c["contributionPoints"] for c in result["components"])
    assert contributions == pytest.approx(result["score"], abs=0.1)
    assert result["band"] == band(result["score"])
    assert "not an individual" in result["disclaimer"]


def test_score_is_bounded_0_to_100():
    huge = make_record("2026-07-01T23:00:00", calories_kcal=5000, sugar_g=500, sodium_mg=20000, saturated_fat_g=300)
    assert compute_indicator(frame([huge]))["score"] == 100.0
    tiny = make_record("2026-07-01T13:00:00", category="FRUITS_VEGETABLES")
    result = compute_indicator(frame([tiny]))
    assert result["score"] == 0.0  # no late-night orders: only the share component (0) is available


def test_missing_nutrients_are_excluded_not_zeroed():
    records = [make_record("2026-07-01T23:00:00", sugar_g=None) for _ in range(3)]
    records.append(make_record("2026-07-01T23:30:00", sugar_g=50.0))
    result = compute_indicator(frame(records))
    sugar = next(c for c in result["components"] if c["key"] == "sugar_per_order")
    assert sugar["available"] is False
    assert sugar["dataCoverage"] == 0.25
    assert "25%" in sugar["unavailableReason"]
    assert result["weightCoverage"] == pytest.approx(1 - DEFAULT_WEIGHTS["sugar_per_order"])


def test_sufficient_coverage_uses_known_values_only():
    records = [make_record("2026-07-01T23:00:00", sugar_g=10.0) for _ in range(3)]
    records.append(make_record("2026-07-01T23:30:00", sugar_g=None))
    sugar = next(c for c in compute_indicator(frame(records))["components"] if c["key"] == "sugar_per_order")
    assert sugar["available"] and sugar["rawValue"] == 10.0


def test_custom_weights_change_the_score():
    df = frame([make_record("2026-07-01T23:00:00")])
    only_sugar = {k: 0.0 for k in DEFAULT_WEIGHTS} | {"sugar_per_order": 1.0}
    assert compute_indicator(df, only_sugar)["score"] == pytest.approx(24.0)


@pytest.mark.parametrize("weights", [{"unknown": 1.0}, {"sugar_per_order": -1}, {"sugar_per_order": float("nan")},
                                     {k: 0.0 for k in DEFAULT_WEIGHTS}])
def test_invalid_weights_rejected(weights):
    with pytest.raises(ValueError):
        validate_weights(weights)
