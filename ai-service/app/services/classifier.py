"""Food-description classification: transparent keyword rules first, trained model as fallback."""
from __future__ import annotations

import logging
import re
from pathlib import Path

from ml.predict import TrainedClassifier
from ml.preprocessing import normalize_text

log = logging.getLogger(__name__)

# Keywords per category. Matching is on whole words / phrases of the normalised description.
RULES: dict[str, list[str]] = {
    "SUGARY_BEVERAGES": [
        "cola", "coke", "pepsi", "soda", "soft drink", "cold coffee", "frappe", "frappuccino", "milkshake",
        "shake", "lassi", "juice", "energy drink", "iced tea", "bubble tea", "hot chocolate", "mocktail",
        "lemonade", "sprite", "thums up", "fanta", "mountain dew", "rose milk", "badam milk", "falooda",
        "mojito", "smoothie", "frooti", "maaza", "chai",
    ],
    "DESSERTS": [
        "gulab jamun", "rasgulla", "rasmalai", "brownie", "ice cream", "cake", "pastry", "cheesecake",
        "kulfi", "halwa", "katli", "barfi", "jalebi", "cookie", "cookies", "donut", "doughnut", "waffle",
        "pancake", "pancakes", "tiramisu", "kheer", "payasam", "shrikhand", "mysore pak", "laddu", "ladoo",
        "cupcake", "sundae", "gelato", "mousse", "pudding", "dessert", "sweets", "mithai", "rabri",
    ],
    "FRIED_SNACKS": [
        "vada pav", "samosa", "pakoda", "pakora", "bhaji", "bajji", "bonda", "fries", "french fries",
        "kachori", "chicken 65", "crispy corn", "chilli potato", "fish fingers", "spring roll", "spring rolls",
        "medu vada", "batata vada", "fried momos", "onion rings", "pani puri", "sev puri", "chips",
        "nuggets", "popcorn chicken", "dabeli", "namkeen", "chakli", "fafda", "wings", "fried",
    ],
    "FAST_FOOD": [
        "burger", "pizza", "garlic bread", "hot dog", "sandwich", "kathi roll", "roll", "shawarma", "frankie",
        "wrap", "noodles", "maggi", "hakka", "fried rice", "pav bhaji", "nachos", "pasta", "tacos", "taco",
        "toast", "sub", "quesadilla", "burrito",
    ],
    "BALANCED_MEALS": [
        "khichdi", "dal", "idli", "sambar", "thali", "roti", "chapati", "quinoa", "grilled", "brown rice",
        "curd rice", "upma", "poha", "oats", "omelette", "cheela", "chilla", "ragi", "millet", "multigrain",
        "whole wheat", "rice bowl", "mini meals", "lentil soup", "stir fry", "tandoori",
    ],
    "FRUITS_VEGETABLES": [
        "fruit", "fruits", "salad", "papaya", "watermelon", "banana", "apple", "berries", "cucumber", "carrot",
        "sprouts", "kachumber", "pomegranate", "guava", "pineapple", "steamed vegetables", "vegetables",
        "grapes", "mango slices", "orange", "sweet potato", "beetroot", "tomato", "chaat", "avocado", "corn",
        "muskmelon",
    ],
    "OTHER": [
        "biryani", "dosa", "momos", "momo", "butter chicken", "makhani", "chole", "bhature",
        "paratha", "naan", "curry", "chaas", "buttermilk", "coconut water", "water", "black coffee",
        "green tea", "rogan josh", "haleem", "kadai", "rajma", "uttapam", "appam", "korma", "misal", "chaap",
        "pulao",
    ],
}
# Used to break ties between categories with the same number of matched keywords.
PRIORITY = ["SUGARY_BEVERAGES", "DESSERTS", "FRIED_SNACKS", "FAST_FOOD", "BALANCED_MEALS",
            "FRUITS_VEGETABLES", "OTHER"]

_PATTERNS = {
    cat: [(kw, re.compile(r"\b" + re.escape(kw) + r"\b")) for kw in kws] for cat, kws in RULES.items()
}


def rule_classify(text: str) -> dict | None:
    """Return the rule-based category with matched keywords, or None when no keyword matches.

    A longer phrase counts once and suppresses the shorter keywords it contains (so "french fries"
    is one match, not two).
    """
    cleaned = normalize_text(text)
    all_hits = [(cat, kw) for cat, patterns in _PATTERNS.items() for kw, pat in patterns if pat.search(cleaned)]
    phrases = {kw for _, kw in all_hits}
    scores: dict[str, list[str]] = {}
    for cat, kw in all_hits:
        # drop keywords contained in a longer matched phrase, across all categories
        if not any(kw != other and kw in other for other in phrases):
            scores.setdefault(cat, []).append(kw)
    if not scores:
        return None
    best = max(scores, key=lambda c: (len(scores[c]), -PRIORITY.index(c)))
    return {"category": best, "matched_keywords": scores[best]}


class FoodClassifier:
    def __init__(self, model: TrainedClassifier | None):
        self.model = model

    @classmethod
    def from_dir(cls, model_dir: Path) -> "FoodClassifier":
        try:
            model = TrainedClassifier.load(model_dir)
        except Exception:  # corrupted or incompatible artefact: degrade to rules only
            log.exception("Could not load trained classifier from %s; using rule-based classification only", model_dir)
            model = None
        if model is None:
            log.warning("No trained classifier found in %s; run 'python -m ml.train'. Rule-based only.", model_dir)
        else:
            log.info("Loaded trained classifier %s", model.version)
        return cls(model)

    @property
    def model_available(self) -> bool:
        return self.model is not None

    def classify(self, texts: list[str], method: str = "auto") -> list[dict]:
        if method == "model" and self.model is None:
            raise ModelUnavailableError("The trained model is not available; use method 'rules' or 'auto'")
        rule_results = [None] * len(texts) if method == "model" else [rule_classify(t) for t in texts]
        need_model = [i for i, r in enumerate(rule_results) if r is None] if method != "rules" else []
        model_results = {}
        if need_model and self.model is not None:
            predictions = self.model.predict([texts[i] for i in need_model])
            model_results = dict(zip(need_model, predictions))

        output = []
        for i, text in enumerate(texts):
            rule = rule_results[i]
            if rule is not None:
                output.append({
                    "text": text, "category": rule["category"], "method": "RULE_BASED", "confidence": None,
                    "matched_keywords": rule["matched_keywords"], "top_features": [], "alternatives": [],
                    "explanation": "Matched keyword(s): " + ", ".join(rule["matched_keywords"])
                                   + ". Rule-based results have no probability.",
                })
            elif i in model_results:
                p = model_results[i]
                feats = ", ".join(f["feature"] for f in p["top_features"][:3]) or "no strong features"
                output.append({
                    "text": text, "category": p["category"], "method": "ML_MODEL", "confidence": p["confidence"],
                    "matched_keywords": [], "top_features": p["top_features"], "alternatives": p["alternatives"],
                    "explanation": f"No keyword rule matched; TF-IDF + logistic regression model predicted "
                                   f"{p['category']} with probability {p['confidence']:.2f}. "
                                   f"Strongest features: {feats}.",
                })
            else:
                output.append({
                    "text": text, "category": "OTHER", "method": "DEFAULT", "confidence": None,
                    "matched_keywords": [], "top_features": [], "alternatives": [],
                    "explanation": "No keyword matched and no trained model was used; defaulted to OTHER.",
                })
        return output


class ModelUnavailableError(RuntimeError):
    pass
