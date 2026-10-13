import pytest

from app.services.classifier import FoodClassifier, ModelUnavailableError, rule_classify


@pytest.mark.parametrize("text,expected", [
    ("Chicken Burger", "FAST_FOOD"),
    ("Margherita Pizza (2 slices)", "FAST_FOOD"),
    ("Schezwan fried rice", "FAST_FOOD"),
    ("Vada Pav", "FRIED_SNACKS"),
    ("Samosa (2 pcs)", "FRIED_SNACKS"),
    ("Cola (500 ml)", "SUGARY_BEVERAGES"),
    ("Mango Lassi", "SUGARY_BEVERAGES"),
    ("Gulab Jamun", "DESSERTS"),
    ("Chocolate Brownie", "DESSERTS"),
    ("Dal Khichdi Bowl", "BALANCED_MEALS"),
    ("Fruit Bowl", "FRUITS_VEGETABLES"),
    ("Green Salad", "FRUITS_VEGETABLES"),
    ("Chicken Biryani", "OTHER"),
    ("Coconut Water", "OTHER"),
])
def test_rule_classification(text, expected):
    result = rule_classify(text)
    assert result is not None and result["category"] == expected
    assert result["matched_keywords"]


def test_rules_return_none_when_nothing_matches():
    assert rule_classify("zzz qqq") is None


def test_auto_uses_rules_first_with_null_confidence(trained_classifier):
    [p] = trained_classifier.classify(["Veg Burger"], "auto")
    assert p["method"] == "RULE_BASED"
    assert p["confidence"] is None
    assert "burger" in p["matched_keywords"]


def test_auto_falls_back_to_model_with_probability(trained_classifier):
    [p] = trained_classifier.classify(["paneer tikka masala"], "auto")
    assert p["method"] == "ML_MODEL"
    assert 0.0 < p["confidence"] <= 1.0
    assert len(p["alternatives"]) == 2
    assert p["top_features"], "model predictions explain their contributing features"
    assert all(f["weight"] > 0 for f in p["top_features"])


def test_model_method_predicts_known_examples(trained_classifier):
    preds = trained_classifier.classify(["cheese burger", "gulab jamun"], "model")
    assert [p["category"] for p in preds] == ["FAST_FOOD", "DESSERTS"]
    assert all(p["method"] == "ML_MODEL" for p in preds)


def test_rules_only_defaults_to_other():
    clf = FoodClassifier(model=None)
    [p] = clf.classify(["zzz qqq"], "rules")
    assert p["category"] == "OTHER" and p["method"] == "DEFAULT"


def test_model_method_without_model_raises():
    with pytest.raises(ModelUnavailableError):
        FoodClassifier(model=None).classify(["pizza"], "model")


def test_training_metadata(trained_classifier):
    meta = trained_classifier.model.meta
    assert meta["nTrainingExamples"] > 100
    assert 0 <= meta["metrics"]["holdoutAccuracy"] <= 1
    assert set(meta["labels"]) == {"FAST_FOOD", "FRIED_SNACKS", "SUGARY_BEVERAGES", "DESSERTS",
                                   "BALANCED_MEALS", "FRUITS_VEGETABLES", "OTHER"}
