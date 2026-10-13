"""HTTP routes of the AI service."""
from __future__ import annotations

import logging
import time

from fastapi import APIRouter, HTTPException, Request

from app.schemas.models import AnalyzeRequest, ClassifyRequest, ClassifyResponse, ClusterRequest
from app.services import analysis, clustering
from app.services.classifier import RULES, ModelUnavailableError
from app.services.indicator import COMPONENTS, DEFAULT_WEIGHTS, INDICATOR_VERSION
from ml.preprocessing import LateNightWindow

log = logging.getLogger(__name__)
router = APIRouter()


def _window(cfg) -> LateNightWindow:
    try:
        return LateNightWindow.parse(cfg.start, cfg.end)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


def _check_size(request: Request, n: int) -> None:
    limit = request.app.state.settings.max_records
    if n > limit:
        raise HTTPException(status_code=413, detail=f"too many records ({n}); the limit is {limit}")


@router.get("/health", tags=["health"])
def health(request: Request) -> dict:
    return {"status": "UP", "service": "nutrinight-ai",
            "modelAvailable": request.app.state.classifier.model_available}


@router.post("/classify", response_model=ClassifyResponse, response_model_by_alias=True, tags=["classification"])
def classify(body: ClassifyRequest, request: Request) -> dict:
    clf = request.app.state.classifier
    try:
        predictions = clf.classify(body.items, body.method)
    except ModelUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    log.info("classified %d items (method=%s)", len(body.items), body.method)
    return {"predictions": predictions, "model_available": clf.model_available,
            "model_version": clf.model.version if clf.model else None}


@router.post("/analyze", tags=["analysis"])
def analyze(body: AnalyzeRequest, request: Request) -> dict:
    _check_size(request, len(body.records))
    started = time.perf_counter()
    try:
        result = analysis.analyze([r.model_dump() for r in body.records], _window(body.late_night_window),
                                  body.indicator_weights)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    log.info("analyzed %d records in %.0f ms", len(body.records), (time.perf_counter() - started) * 1000)
    return result


@router.post("/cluster", tags=["analysis"])
def cluster(body: ClusterRequest, request: Request) -> dict:
    _check_size(request, len(body.records))
    started = time.perf_counter()
    try:
        result = clustering.cluster([r.model_dump() for r in body.records], _window(body.late_night_window),
                                    body.late_night_only, body.max_clusters, body.min_orders_per_unit)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    log.info("clustered %d records in %.0f ms", len(body.records), (time.perf_counter() - started) * 1000)
    return result


@router.get("/model-info", tags=["model"])
def model_info(request: Request) -> dict:
    clf = request.app.state.classifier
    meta = clf.model.meta if clf.model else None
    return {
        "classifier": {
            "available": clf.model_available,
            **(meta or {"note": "No trained model is loaded. Run 'python -m ml.train' to train one; "
                                "classification falls back to keyword rules."}),
        },
        "ruleBased": {
            "description": "Keyword rules on the normalised description; longest phrase wins, ties broken by "
                           "category priority. Rule results carry no probability.",
            "categories": {cat: len(kws) for cat, kws in RULES.items()},
            "keywordCount": sum(len(kws) for kws in RULES.values()),
        },
        "classificationStrategy": "auto = keyword rules first; trained model only when no rule matches; "
                                  "OTHER (method DEFAULT) when neither applies.",
        "indicator": {
            "version": INDICATOR_VERSION,
            "defaultWeights": DEFAULT_WEIGHTS,
            "references": {c.key: {"value": c.reference, "unit": c.unit} for c in COMPONENTS},
        },
        "clustering": {"algorithm": "K-Means on standardised city-night features, k by silhouette"},
    }
