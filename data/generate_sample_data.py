"""Generate the reproducible SYNTHETIC sample dataset for NutriNight AI.

Every record produced here is invented for local development and testing.
Nutrition values are rounded, illustrative per-serving estimates in the range
typically reported for these dishes; they are NOT verified restaurant data.

Usage:  python data/generate_sample_data.py  [--orders 6000] [--seed 42]
"""
from __future__ import annotations

import argparse
import csv
import random
from datetime import datetime, timedelta
from pathlib import Path

NUTRITION_SOURCE = "Illustrative estimate (synthetic, not verified)"

# name, category, serving size, unit, kcal, sugar g, sodium mg, sat fat g, protein g
# None = value deliberately missing (to exercise missing-data handling).
FOOD_ITEMS = [
    ("Vada Pav", "FRIED_SNACKS", 150, "g", 290, 3, 560, 3.5, 7),
    ("Samosa (2 pcs)", "FRIED_SNACKS", 120, "g", 310, 2, 420, 5, 6),
    ("French Fries (medium)", "FRIED_SNACKS", 117, "g", 365, 0.3, 246, 2.3, 4),
    ("Onion Pakoda", "FRIED_SNACKS", 100, "g", 300, 3, 480, 3, 6),
    ("Chicken Biryani", "OTHER", 350, "g", 620, 4, 1150, 7, 30),
    ("Veg Biryani", "OTHER", 350, "g", 520, 5, 980, 5, 12),
    ("Masala Dosa", "OTHER", 250, "g", 390, 4, 700, 4, 9),
    ("Chicken Momos (steamed, 8 pcs)", "OTHER", 200, "g", 340, None, 690, None, 18),
    ("Margherita Pizza (2 slices)", "FAST_FOOD", 200, "g", 530, 6, 1100, 10, 22),
    ("Pepperoni Pizza (2 slices)", "FAST_FOOD", 210, "g", 610, 6, 1350, 12, 26),
    ("Chicken Burger", "FAST_FOOD", 180, "g", 480, 7, 950, 8, 25),
    ("Veg Burger", "FAST_FOOD", 170, "g", 420, 7, 800, 6, 11),
    ("Paneer Kathi Roll", "FAST_FOOD", 200, "g", 450, 4, 850, 9, 18),
    ("Instant Noodles (masala)", "FAST_FOOD", 280, "g", 380, 3, 1300, 7, 8),
    ("Pav Bhaji", "FAST_FOOD", 300, "g", 560, 9, 1200, 11, 13),
    ("Cola (500 ml)", "SUGARY_BEVERAGES", 500, "ml", 210, 53, 20, 0, 0),
    ("Cold Coffee", "SUGARY_BEVERAGES", 350, "ml", 280, 35, 150, 6, 8),
    ("Mango Lassi", "SUGARY_BEVERAGES", 300, "ml", 290, 40, 110, 3.5, 8),
    ("Mixed Fruit Juice (packaged)", "SUGARY_BEVERAGES", 300, "ml", 150, 33, None, 0, 1),
    ("Gulab Jamun (2 pcs)", "DESSERTS", 100, "g", 380, 50, 40, 6, 5),
    ("Chocolate Brownie", "DESSERTS", 90, "g", 410, 35, 180, 8, 5),
    ("Ice Cream Tub", "DESSERTS", 125, "g", 260, 26, 80, 9, 4),
    ("Fruit Bowl", "FRUITS_VEGETABLES", 250, "g", 140, 25, 10, 0.2, 2),
    ("Green Salad", "FRUITS_VEGETABLES", 200, "g", 90, 6, 180, 0.5, 3),
    ("Sprouts Chaat", "FRUITS_VEGETABLES", 200, "g", 180, 5, 320, 0.4, 11),
    ("Dal Khichdi Bowl", "BALANCED_MEALS", 350, "g", 380, 4, 650, 2.5, 14),
    ("Grilled Chicken Rice Bowl", "BALANCED_MEALS", 400, "g", 520, 6, 780, 3, 38),
    ("Idli Sambar (3 idli)", "BALANCED_MEALS", 300, "g", 290, 5, 600, 0.5, 10),
    ("Whole Wheat Veg Sandwich", "BALANCED_MEALS", 200, "g", 300, 5, 560, 2, 11),
    ("Buttermilk (Chaas)", "OTHER", 250, "ml", 60, 5, 280, 1, 4),
    ("Coconut Water", "OTHER", 300, "ml", 60, 9, 75, 0, 1),
]

# City -> relative order volume. "Nashik" is intentionally small so the
# small-group privacy suppression can be demonstrated.
CITIES = {
    "Mumbai": 22, "Bengaluru": 18, "Delhi": 18, "Pune": 12, "Hyderabad": 12,
    "Chennai": 9, "Kolkata": 7, "Ahmedabad": 5, "Nashik": 0.08,
}

# Relative order volume per local hour (0-23).
HOUR_WEIGHTS = [
    5, 4, 3, 1.5, 0.5, 0.3, 0.5, 1, 2, 3, 4, 5,
    9, 10, 7, 4, 4, 5, 7, 9, 11, 10, 9, 7,
]

LATE_NIGHT_HOURS = {22, 23, 0, 1, 2, 3}
DISCRETIONARY = {"FAST_FOOD", "FRIED_SNACKS", "SUGARY_BEVERAGES", "DESSERTS"}

COLUMNS = [
    "order_id", "order_time", "food_item", "food_category", "quantity",
    "serving_size_g", "serving_unit", "calories_kcal", "sugar_g", "sodium_mg",
    "saturated_fat_g", "protein_g", "city", "data_source", "nutrition_source",
]


def _fmt(value):
    return "" if value is None else value


def item_weights(hour: int, weekend: bool) -> list[float]:
    """Late-night and weekend hours lean towards discretionary foods (synthetic assumption)."""
    weights = []
    for _, category, *_ in FOOD_ITEMS:
        w = 1.0
        if hour in LATE_NIGHT_HOURS:
            w *= 1.8 if category in DISCRETIONARY else 0.6
            if weekend and category in DISCRETIONARY:
                w *= 1.2
        weights.append(w)
    return weights


def build_row(order_no: int, ts: datetime, rng: random.Random, item=None) -> dict:
    weekend = ts.weekday() >= 5
    if item is None:
        item = rng.choices(FOOD_ITEMS, weights=item_weights(ts.hour, weekend))[0]
    name, category, size, unit, kcal, sugar, sodium, satfat, protein = item
    return {
        "order_id": f"SYN-{order_no:06d}",
        "order_time": ts.strftime("%Y-%m-%dT%H:%M:%S"),
        "food_item": name,
        "food_category": category,
        "quantity": rng.choices([1, 2, 3], weights=[78, 18, 4])[0],
        "serving_size_g": size,
        "serving_unit": unit,
        "calories_kcal": _fmt(kcal),
        "sugar_g": _fmt(sugar),
        "sodium_mg": _fmt(sodium),
        "saturated_fat_g": _fmt(satfat),
        "protein_g": _fmt(protein),
        "city": rng.choices(list(CITIES), weights=list(CITIES.values()))[0],
        "data_source": "SYNTHETIC",
        "nutrition_source": NUTRITION_SOURCE,
    }


def generate(n_orders: int, seed: int, start: datetime, days: int) -> list[dict]:
    rng = random.Random(seed)
    rows = []
    for i in range(1, n_orders + 1):
        day = start + timedelta(days=rng.randrange(days))
        hour = rng.choices(range(24), weights=HOUR_WEIGHTS)[0]
        ts = day.replace(hour=hour, minute=rng.randrange(60), second=rng.randrange(60))
        rows.append(build_row(i, ts, rng))

    # Deterministic boundary cases for the 22:00 (inclusive) - 04:00 (exclusive) window.
    boundary_times = ["21:59:59", "22:00:00", "23:00:00", "00:00:00", "03:59:59", "04:00:00"]
    for j, t in enumerate(boundary_times):
        hh, mm, ss = map(int, t.split(":"))
        ts = (start + timedelta(days=10)).replace(hour=hh, minute=mm, second=ss)
        rows.append(build_row(n_orders + j + 1, ts, rng))

    rows.sort(key=lambda r: r["order_time"])
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--orders", type=int, default=6000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--start", default="2026-07-01")
    parser.add_argument("--days", type=int, default=92)
    parser.add_argument("--out", default=str(Path(__file__).with_name("sample_food_orders.csv")))
    args = parser.parse_args()

    rows = generate(args.orders, args.seed, datetime.fromisoformat(args.start), args.days)
    with open(args.out, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} SYNTHETIC orders to {args.out}")


if __name__ == "__main__":
    main()
