"""Inference with the trained classifier, including per-prediction feature contributions."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from joblib import load

from ml.preprocessing import normalize_text


class TrainedClassifier:
    def __init__(self, pipeline, meta: dict):
        self.pipeline = pipeline
        self.meta = meta
        self._features = pipeline.named_steps["features"]
        self._clf = pipeline.named_steps["clf"]
        names = []
        for prefix, vectorizer in self._features.transformer_list:
            names.extend(f"{prefix}:{n}" for n in vectorizer.get_feature_names_out())
        self._feature_names = np.array(names)

    @classmethod
    def load(cls, model_dir: Path) -> "TrainedClassifier | None":
        model_path = model_dir / "food_classifier.joblib"
        meta_path = model_dir / "food_classifier.meta.json"
        if not model_path.exists() or not meta_path.exists():
            return None
        return cls(load(model_path), json.loads(meta_path.read_text(encoding="utf-8")))

    @property
    def version(self) -> str:
        return self.meta.get("modelVersion", "unknown")

    def predict(self, texts: list[str], top_features: int = 5) -> list[dict]:
        cleaned = [normalize_text(t) for t in texts]
        x = self._features.transform(cleaned)
        probabilities = self._clf.predict_proba(x)
        classes = self._clf.classes_
        results = []
        for i in range(len(cleaned)):
            order = np.argsort(probabilities[i])[::-1]
            best = order[0]
            row = x[i]
            # Contribution of each present feature to the predicted class's linear score.
            contrib = row.multiply(self._clf.coef_[best]).toarray().ravel()
            idx = [j for j in np.argsort(contrib)[::-1][:top_features] if contrib[j] > 0]
            results.append({
                "category": str(classes[best]),
                "confidence": round(float(probabilities[i][best]), 4),
                "alternatives": [
                    {"category": str(classes[j]), "probability": round(float(probabilities[i][j]), 4)}
                    for j in order[1:3]
                ],
                "top_features": [
                    {"feature": str(self._feature_names[j]), "weight": round(float(contrib[j]), 4)} for j in idx
                ],
            })
        return results
