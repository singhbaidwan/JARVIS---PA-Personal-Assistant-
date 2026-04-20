from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class EventInput(BaseModel):
    type: str
    timestamp: datetime | None = None
    payload: dict[str, Any] = Field(default_factory=dict)
    source: str | None = None


class PredictRequest(BaseModel):
    events: list[EventInput] = Field(default_factory=list)
    reference_time: datetime | None = None


class PredictResponse(BaseModel):
    predicted_action: str
    confidence: float
    reason: str
    context: dict[str, Any] = Field(default_factory=dict)


class LlmRequest(BaseModel):
    prompt: str
    provider: str | None = None
    system_prompt: str | None = "You are JARVIS, a concise and practical personal assistant."
    model: str | None = None
    temperature: float = Field(default=0.3, ge=0.0, le=2.0)
    max_tokens: int = Field(default=300, ge=16, le=4096)


class LlmResponse(BaseModel):
    response: str
    model: str
    provider: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class AnomalyRequest(BaseModel):
    events: list[EventInput] = Field(default_factory=list)
    reference_time: datetime | None = None


class AnomalyResponse(BaseModel):
    anomaly_detected: bool
    reason: str
    severity: str
    score: float
    signals: list[str] = Field(default_factory=list)


class RecommendationRequest(BaseModel):
    events: list[EventInput] = Field(default_factory=list)
    reference_time: datetime | None = None


class Recommendation(BaseModel):
    category: str
    message: str
    confidence: float
    details: dict[str, Any] = Field(default_factory=dict)


class RecommendationResponse(BaseModel):
    focus_score: float
    peak_productivity_window: str | None = None
    summary: str
    recommendations: list[Recommendation] = Field(default_factory=list)
