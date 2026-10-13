from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from tests.conftest import synthetic_records


def as_json(records):
    out = []
    for r in records:
        r = dict(r)
        r["localDateTime"] = r.pop("local_date_time").isoformat()
        for snake, camel in (("calories_kcal", "caloriesKcal"), ("sugar_g", "sugarG"), ("sodium_mg", "sodiumMg"),
                             ("saturated_fat_g", "saturatedFatG"), ("protein_g", "proteinG")):
            r[camel] = r.pop(snake)
        out.append(r)
    return out


def test_health_is_open(trained_classifier):
    app = create_app(Settings(api_key="secret"), classifier=trained_classifier)
    with TestClient(app) as c:
        assert c.get("/health").json()["status"] == "UP"
        assert c.post("/classify", json={"items": ["pizza"]}).status_code == 401
        assert c.post("/classify", json={"items": ["pizza"]}, headers={"X-API-Key": "wrong"}).status_code == 401


def test_classify_endpoint(client):
    res = client.post("/classify", json={"items": ["Veg Burger", "paneer tikka masala"], "method": "auto"})
    assert res.status_code == 200
    body = res.json()
    assert body["modelAvailable"] is True
    rule, model = body["predictions"]
    assert rule["method"] == "RULE_BASED" and rule["confidence"] is None and rule["matchedKeywords"] == ["burger"]
    assert model["method"] == "ML_MODEL" and model["confidence"] is not None and model["topFeatures"]


def test_classify_validation_errors(client):
    for payload in ({"items": []}, {"items": [""]}, {"items": ["x" * 201]}, {"items": ["a"], "method": "magic"},
                    {"items": ["a"], "unexpected": 1}):
        res = client.post("/classify", json=payload)
        assert res.status_code == 422, payload
        assert res.json()["error"]["code"] == "VALIDATION_ERROR"


def test_analyze_endpoint(client):
    res = client.post("/analyze", json={"records": as_json(synthetic_records())})
    assert res.status_code == 200, res.text
    body = res.json()
    s = body["summary"]
    assert s["totalOrders"] == 20 * 3 * 5
    assert s["lateNightOrders"] == 20 * 3 * 4
    assert s["lateNightShare"] == 0.8
    assert s["peakLateNightHour"] in (22, 23, 0, 1)
    assert len(body["hourly"]) == 24
    assert {d["dayType"] for d in body["dayTypes"]} == {"WEEKDAY", "WEEKEND"}
    assert 0 <= body["indicator"]["score"] <= 100
    assert body["insights"] and body["methodology"]
    assert body["recurrence"]["available"] is True


def test_analyze_rejects_invalid_records(client):
    bad = as_json(synthetic_records(1))[:1]
    bad[0]["quantity"] = 0
    assert client.post("/analyze", json={"records": bad}).status_code == 422
    bad = as_json(synthetic_records(1))[:1]
    bad[0]["sugarG"] = -1
    assert client.post("/analyze", json={"records": bad}).status_code == 422
    bad = as_json(synthetic_records(1))[:1]
    bad[0]["category"] = "PIZZA"
    assert client.post("/analyze", json={"records": bad}).status_code == 422
    assert client.post("/analyze", json={"records": []}).status_code == 422
    ok = as_json(synthetic_records(1))
    res = client.post("/analyze", json={"records": ok, "indicatorWeights": {"bogus": 1}})
    assert res.status_code == 422 and "bogus" in res.json()["error"]["message"]
    res = client.post("/analyze", json={"records": ok, "lateNightWindow": {"start": "22:00", "end": "22:00"}})
    assert res.status_code == 422


def test_record_limit(client):
    records = as_json(synthetic_records(1)) * 400  # 6000 > 5000 test limit
    assert client.post("/analyze", json={"records": records}).status_code == 413


def test_cluster_endpoint(client):
    res = client.post("/cluster", json={"records": as_json(synthetic_records(20)), "maxClusters": 4})
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["available"] is True
    assert 2 <= body["k"] <= 4
    assert -1 <= body["silhouette"] <= 1
    assert body["quality"] in {"STRONG", "REASONABLE", "WEAK"}
    assert sum(c["size"] for c in body["clusters"]) == body["unitsAnalyzed"]
    assert "not diagnoses" in body["disclaimer"]
    # the synthetic data has two clearly different night types
    assert body["quality"] == "STRONG"


def test_cluster_insufficient_units(client):
    res = client.post("/cluster", json={"records": as_json(synthetic_records(1))})
    assert res.status_code == 200
    assert res.json()["available"] is False


def test_model_info(client):
    body = client.get("/model-info").json()
    assert body["classifier"]["available"] is True
    assert body["classifier"]["modelVersion"].startswith("1.0.0+")
    assert body["ruleBased"]["keywordCount"] > 50
    assert body["indicator"]["defaultWeights"]


def test_unknown_route_structured_404(client):
    res = client.get("/nope")
    assert res.status_code == 404 and res.json()["error"]["code"] == "NOT_FOUND"
