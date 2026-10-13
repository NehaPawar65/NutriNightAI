"""Request/response schemas. JSON uses camelCase; Python attributes use snake_case."""
from __future__ import annotations

from datetime import date, datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

Category = Literal["FAST_FOOD", "FRIED_SNACKS", "SUGARY_BEVERAGES", "DESSERTS", "BALANCED_MEALS",
                   "FRUITS_VEGETABLES", "OTHER"]


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class ClassifyRequest(CamelModel):
    items: list[str] = Field(min_length=1, max_length=500, description="Food descriptions to classify")
    method: Literal["auto", "rules", "model"] = "auto"

    @field_validator("items")
    @classmethod
    def check_items(cls, items: list[str]) -> list[str]:
        for i, text in enumerate(items):
            if not text or not text.strip():
                raise ValueError(f"item {i} is empty")
            if len(text) > 200:
                raise ValueError(f"item {i} is longer than 200 characters")
        return [t.strip() for t in items]


class FeatureWeight(CamelModel):
    feature: str
    weight: float


class AlternativeCategory(CamelModel):
    category: str
    probability: float


class Prediction(CamelModel):
    text: str
    category: Category
    method: Literal["RULE_BASED", "ML_MODEL", "DEFAULT"]
    confidence: Optional[float] = Field(None, description="Model probability; null for rule-based results")
    matched_keywords: list[str] = []
    top_features: list[FeatureWeight] = []
    alternatives: list[AlternativeCategory] = []
    explanation: str


class ClassifyResponse(CamelModel):
    predictions: list[Prediction]
    model_available: bool
    model_version: Optional[str] = None


class OrderRecord(CamelModel):
    """One order line with per-serving nutrients. Missing nutrients must be null, not 0."""
    local_date_time: datetime
    analysis_date: Optional[date] = None
    late_night: Optional[bool] = None
    category: Category
    quantity: int = Field(1, ge=1, le=100)
    city: str = Field("Unknown", min_length=1, max_length=100)
    calories_kcal: Optional[float] = Field(None, ge=0, le=5000)
    sugar_g: Optional[float] = Field(None, ge=0, le=500)
    sodium_mg: Optional[float] = Field(None, ge=0, le=20000)
    saturated_fat_g: Optional[float] = Field(None, ge=0, le=300)
    protein_g: Optional[float] = Field(None, ge=0, le=300)


class WindowConfig(CamelModel):
    start: str = Field("22:00", pattern=r"^\d{2}:\d{2}$")
    end: str = Field("04:00", pattern=r"^\d{2}:\d{2}$")


class AnalyzeRequest(CamelModel):
    records: list[OrderRecord] = Field(min_length=1)
    late_night_window: WindowConfig = WindowConfig()
    indicator_weights: Optional[dict[str, float]] = None


class ClusterRequest(CamelModel):
    records: list[OrderRecord] = Field(min_length=1)
    late_night_window: WindowConfig = WindowConfig()
    late_night_only: bool = True
    max_clusters: int = Field(5, ge=2, le=8)
    min_orders_per_unit: int = Field(3, ge=1, le=100)


class ErrorBody(BaseModel):
    code: str
    message: str
    details: list[dict[str, Any]] = []


class ErrorResponse(BaseModel):
    error: ErrorBody
