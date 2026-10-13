"""Evaluation helpers for the food-description classifier."""
from __future__ import annotations

import numpy as np
from sklearn.base import clone
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split


def evaluate_pipeline(pipeline, texts: list[str], labels: list[str], random_state: int = 42) -> dict:
    """Hold-out evaluation (stratified 25%) plus 5-fold stratified cross-validation of macro-F1.

    The training examples are short, hand-labelled, illustrative descriptions; these metrics measure
    agreement with those labels and say nothing about real-world nutritional quality.
    """
    x_train, x_test, y_train, y_test = train_test_split(
        texts, labels, test_size=0.25, stratify=labels, random_state=random_state)
    holdout = clone(pipeline).fit(x_train, y_train)
    predicted = holdout.predict(x_test)

    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=random_state)
    cv_scores = cross_val_score(clone(pipeline), texts, labels, cv=cv, scoring="f1_macro")

    report = classification_report(y_test, predicted, output_dict=True, zero_division=0)
    per_class = {
        label: {"precision": round(v["precision"], 3), "recall": round(v["recall"], 3),
                "f1": round(v["f1-score"], 3), "support": int(v["support"])}
        for label, v in report.items() if label not in ("accuracy", "macro avg", "weighted avg")
    }
    return {
        "holdoutAccuracy": round(float(accuracy_score(y_test, predicted)), 3),
        "holdoutMacroF1": round(float(f1_score(y_test, predicted, average="macro")), 3),
        "cvMacroF1Mean": round(float(np.mean(cv_scores)), 3),
        "cvMacroF1Std": round(float(np.std(cv_scores)), 3),
        "cvFolds": 5,
        "holdoutSize": len(y_test),
        "trainSize": len(y_train),
        "perClass": per_class,
    }
