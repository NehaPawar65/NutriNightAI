import os
from datetime import datetime, timedelta
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

os.environ["NUTRINIGHT_SKIP_DOTENV"] = "1"

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402
from app.services.classifier import FoodClassifier  # noqa: E402
from ml.train import train  # noqa: E402

API_KEY = "test-key"


@pytest.fixture(scope="session")
def model_dir(tmp_path_factory) -> Path:
    out = tmp_path_factory.mktemp("models")
    train(out_dir=out)
    return out


@pytest.fixture(scope="session")
def trained_classifier(model_dir) -> FoodClassifier:
    return FoodClassifier.from_dir(model_dir)


@pytest.fixture
def client(trained_classifier) -> TestClient:
    app = create_app(Settings(api_key=API_KEY, max_records=5000), classifier=trained_classifier)
    with TestClient(app, headers={"X-API-Key": API_KEY}) as c:
        yield c


def make_record(ts: str, category: str = "FAST_FOOD", quantity: int = 1, city: str = "Pune", **nutrients) -> dict:
    base = {"calories_kcal": 500.0, "sugar_g": 6.0, "sodium_mg": 1000.0, "saturated_fat_g": 9.0, "protein_g": 20.0}
    base.update(nutrients)
    return {"local_date_time": datetime.fromisoformat(ts), "category": category, "quantity": quantity,
            "city": city, **base}


def synthetic_records(nights: int = 20, cities=("Pune", "Mumbai", "Delhi")) -> list[dict]:
    """Deterministic records: each city-night gets a mix of late-night and daytime orders."""
    records = []
    start = datetime(2026, 7, 1)
    for d in range(nights):
        day = start + timedelta(days=d)
        for ci, city in enumerate(cities):
            heavy = (d + ci) % 2 == 0
            for h in (22, 23, 0, 1):
                ts = day.replace(hour=h) + (timedelta(days=1) if h < 4 else timedelta())
                if heavy:
                    records.append(make_record(ts.isoformat(), "FAST_FOOD", 2, city))
                else:
                    records.append(make_record(ts.isoformat(), "FRUITS_VEGETABLES", 1, city, calories_kcal=140.0,
                                               sugar_g=20.0, sodium_mg=10.0, saturated_fat_g=0.2, protein_g=2.0))
            records.append(make_record(day.replace(hour=13).isoformat(), "BALANCED_MEALS", 1, city,
                                       calories_kcal=380.0, sugar_g=4.0, sodium_mg=650.0, saturated_fat_g=2.5))
    return records
