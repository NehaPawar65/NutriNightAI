"""Train the TF-IDF + Logistic Regression food-description classifier.

Usage (from ai-service/):  python -m ml.train [--data ml/data/food_labels.csv] [--out app/models]

Writes food_classifier.joblib and food_classifier.meta.json (version, dataset hash, metrics).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
import sklearn
from joblib import dump
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline

from ml.evaluate import evaluate_pipeline
from ml.preprocessing import CATEGORIES, normalize_text

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATA = ROOT / "ml" / "data" / "food_labels.csv"
DEFAULT_OUT = ROOT / "app" / "models"
MODEL_FILE = "food_classifier.joblib"
META_FILE = "food_classifier.meta.json"
MODEL_NAME = "food-category-tfidf-logreg"

log = logging.getLogger(__name__)


def build_pipeline() -> Pipeline:
    features = FeatureUnion([
        ("word", TfidfVectorizer(analyzer="word", ngram_range=(1, 2), min_df=1, sublinear_tf=True)),
        ("char", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1, sublinear_tf=True)),
    ])
    classifier = LogisticRegression(max_iter=2000, C=5.0, class_weight="balanced", random_state=42)
    return Pipeline([("features", features), ("clf", classifier)])


def load_dataset(path: Path) -> tuple[list[str], list[str], str]:
    raw = path.read_bytes()
    df = pd.read_csv(path)
    if not {"text", "label"} <= set(df.columns):
        raise ValueError("training data needs 'text' and 'label' columns")
    df = df.dropna(subset=["text", "label"])
    unknown = set(df["label"]) - set(CATEGORIES)
    if unknown:
        raise ValueError(f"unknown labels in training data: {sorted(unknown)}")
    texts = [normalize_text(t) for t in df["text"]]
    dataset_version = "food_labels-" + hashlib.sha256(raw).hexdigest()[:10]
    return texts, df["label"].tolist(), dataset_version


def train(data_path: Path = DEFAULT_DATA, out_dir: Path = DEFAULT_OUT) -> dict:
    texts, labels, dataset_version = load_dataset(data_path)
    pipeline = build_pipeline()
    metrics = evaluate_pipeline(pipeline, texts, labels)
    pipeline.fit(texts, labels)  # final model uses all labelled examples

    out_dir.mkdir(parents=True, exist_ok=True)
    dump(pipeline, out_dir / MODEL_FILE)
    meta = {
        "modelName": MODEL_NAME,
        "modelVersion": f"1.0.0+{dataset_version.split('-')[1]}",
        "datasetVersion": dataset_version,
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "algorithm": "TF-IDF (word 1-2 grams + char 3-5 grams) + multinomial Logistic Regression",
        "sklearnVersion": sklearn.__version__,
        "nTrainingExamples": len(texts),
        "labels": sorted(set(labels)),
        "metrics": metrics,
        "note": "Trained on a small hand-labelled illustrative dataset; metrics reflect agreement with "
                "those labels only.",
    }
    (out_dir / META_FILE).write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    meta = train(args.data, args.out)
    m = meta["metrics"]
    print(f"Trained {meta['modelName']} {meta['modelVersion']} on {meta['nTrainingExamples']} examples: "
          f"hold-out accuracy {m['holdoutAccuracy']}, macro-F1 {m['holdoutMacroF1']}, "
          f"CV macro-F1 {m['cvMacroF1Mean']} +/- {m['cvMacroF1Std']}")


if __name__ == "__main__":
    main()
