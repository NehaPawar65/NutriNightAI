"""NutriNight AI - machine-learning service (FastAPI).

Internal service called by the Spring Boot backend. When AI_SERVICE_API_KEY is set, every endpoint
except /health requires the X-API-Key header.
"""
from __future__ import annotations

import logging
import secrets
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from starlette.exceptions import HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings, load_settings
from app.routes.api import router
from app.services.classifier import FoodClassifier

OPEN_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}


def create_app(settings: Settings | None = None, classifier: FoodClassifier | None = None) -> FastAPI:
    settings = settings or load_settings()
    logging.basicConfig(level=settings.log_level,
                        format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if getattr(app.state, "classifier", None) is None:
            app.state.classifier = FoodClassifier.from_dir(settings.model_dir)
        yield

    app = FastAPI(title="NutriNight AI - ML service", version="1.0.0", lifespan=lifespan,
                  description="Food classification, late-night pattern analysis, dietary indicator and clustering.")
    app.state.settings = settings
    app.state.classifier = classifier

    @app.middleware("http")
    async def require_api_key(request: Request, call_next):
        if settings.api_key and request.url.path not in OPEN_PATHS:
            supplied = request.headers.get("X-API-Key", "")
            if not secrets.compare_digest(supplied.encode(), settings.api_key.encode()):
                return _error(401, "UNAUTHORIZED", "missing or invalid X-API-Key")
        return await call_next(request)

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError):
        details = [{"field": ".".join(str(p) for p in e["loc"][1:]) or "body", "message": e["msg"]}
                   for e in exc.errors()[:50]]
        return _error(422, "VALIDATION_ERROR", "request validation failed", details)

    @app.exception_handler(HTTPException)
    async def http_handler(request: Request, exc: HTTPException):
        codes = {404: "NOT_FOUND", 413: "PAYLOAD_TOO_LARGE", 422: "UNPROCESSABLE", 503: "MODEL_UNAVAILABLE"}
        return _error(exc.status_code, codes.get(exc.status_code, "ERROR"), str(exc.detail))

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):
        logging.getLogger("app").exception("unhandled error on %s", request.url.path)
        return _error(500, "INTERNAL_ERROR", "an unexpected error occurred")

    app.include_router(router)
    return app


def _error(status: int, code: str, message: str, details: list | None = None) -> JSONResponse:
    return JSONResponse(status_code=status,
                        content={"error": {"code": code, "message": message, "details": details or []}})


app = create_app()
